/*******************************************************************************
 * Copyright (c) 2013 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.internal.server.servlets.docker;

import java.io.*;
import java.net.*;
import java.nio.charset.Charset;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.Date;
import org.json.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility to handle requests to and from the Docker Remote API.
 * 
 * @author Anthony Hunter
 */
public class DockerServer {

	private URI dockerServer;

	public DockerServer(URI dockerServer) {
		super();
		this.dockerServer = dockerServer;
	}

	public DockerResponse attachDockerContainer(String containerId, String command) {
		DockerResponse dockerResponse = new DockerResponse();
		HttpURLConnection httpURLConnection = null;
		StringBuilder stringBuilder = new StringBuilder();
		try {
			URL dockerAttachURL = new URL(dockerServer.toString() + "/containers/" + containerId + "/attach?stream=1&stdout=1&stdin=1&stderr=1");
			httpURLConnection = (HttpURLConnection) dockerAttachURL.openConnection();
			httpURLConnection.setDoOutput(true);
			httpURLConnection.setDoInput(true);
			httpURLConnection.setConnectTimeout(10 * 1000);
			httpURLConnection.setReadTimeout(5 * 1000);
			httpURLConnection.setAllowUserInteraction(false);
			//httpURLConnection.setRequestProperty("Content-Type", "application/vnd.docker.raw-stream");
			httpURLConnection.setRequestProperty("Content-Type", "text/plain; charset=utf-8");
			httpURLConnection.setRequestMethod("POST");
			httpURLConnection.connect();
			OutputStream outputStream = httpURLConnection.getOutputStream();
			outputStream.write(command.getBytes());
			outputStream.flush();
			outputStream.close();

			getDockerResponse(dockerResponse, httpURLConnection);

			InputStream inputStream = httpURLConnection.getInputStream();
			BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
			String line = null;
			while ((line = bufferedReader.readLine()) != null) {
				stringBuilder.append(line).append("\n");
			}
			inputStream.close();
			dockerResponse.setStatusMessage(stringBuilder.toString());
			/*
			// first read the 8 byte header
			byte[] header = new byte[8];
			int count = inputStream.read(header);
			if (count != 8) {
				System.err.println("something is wrong, cannot read first 8 bytes");
			}
			System.out.println(new String(header));

			// get the size of the characters in the response
			long uint32 = ((header[4] & 0xFF) << 24) | ((header[5] & 0xFF) << 16) | ((header[6] & 0xFF) << 8) | (header[7] & 0xFF);
			if (uint32 > Integer.MAX_VALUE) {
				System.err.println("buffer is too big, something is wrong");
			}
			int size = (int) uint32;

			// get the rest of the response
			byte[] chars = new byte[size];
			count = inputStream.read(chars);
			if (count != size) {
				System.err.println("something is wrong, size is not right");
			}

			inputStream.close();
			String result = new String(chars);
			System.out.print(result);
			*/
			inputStream.close();
		} catch (IOException e) {
			if (stringBuilder.length() > 0) {
				// the input stream is hanging, put what we have received in the response.
				dockerResponse.setStatusMessage(stringBuilder.toString());
			} else {
				setDockerResponse(dockerResponse, e);
			}
		} finally {
			httpURLConnection.disconnect();
		}
		return dockerResponse;
	}

	public DockerContainer createDockerContainer(String imageName, String containerName) {
		DockerContainer dockerContainer = new DockerContainer();
		HttpURLConnection httpURLConnection = null;
		try {
			JSONObject requestJSONObject = new JSONObject();
			JSONArray cmdArray = new JSONArray();
			cmdArray.put("bash");
			requestJSONObject.put("Cmd", cmdArray);
			requestJSONObject.put("Image", imageName);
			requestJSONObject.put("AttachStdin", true);
			requestJSONObject.put("AttachStdout", true);
			requestJSONObject.put("AttachStderr", true);
			requestJSONObject.put("Tty", true);
			requestJSONObject.put("OpenStdin", true);
			requestJSONObject.put("StdinOnce", true);

			byte[] outputBytes = requestJSONObject.toString().getBytes("UTF-8");

			URL dockerVersionURL = new URL(dockerServer.toString() + "/containers/create?name=" + containerName);
			httpURLConnection = (HttpURLConnection) dockerVersionURL.openConnection();
			httpURLConnection.setDoOutput(true);
			httpURLConnection.setRequestProperty("Content-Type", "application/json");
			httpURLConnection.setRequestProperty("Accept", "application/json");
			httpURLConnection.setRequestMethod("POST");
			httpURLConnection.connect();
			OutputStream outputStream = httpURLConnection.getOutputStream();
			outputStream.write(outputBytes);

			if (getDockerResponse(dockerContainer, httpURLConnection).equals(DockerResponse.StatusCode.CREATED)) {
				JSONObject jsonObject = readDockerResponseAsJSONObject(dockerContainer, httpURLConnection);
				String id = jsonObject.getString(DockerContainer.ID);
				dockerContainer = getDockerContainer(id);
				dockerContainer.setStatusCode(DockerResponse.StatusCode.CREATED);
			}
			outputStream.close();
		} catch (IOException e) {
			setDockerResponse(dockerContainer, e);
		} catch (JSONException e) {
			setDockerResponse(dockerContainer, e);
		} finally {
			httpURLConnection.disconnect();
		}
		return dockerContainer;
	}

	public DockerResponse deleteDockerContainer(String containerId) {
		DockerResponse dockerResponse = new DockerResponse();
		HttpURLConnection httpURLConnection = null;
		try {
			URL dockerDeleteURL = new URL(dockerServer.toString() + "/containers/" + containerId);
			httpURLConnection = (HttpURLConnection) dockerDeleteURL.openConnection();
			httpURLConnection.setRequestMethod("DELETE");
			httpURLConnection.connect();

			if (getDockerResponse(dockerResponse, httpURLConnection).equals(DockerResponse.StatusCode.STARTED)) {
				// generic method returns 204 - no error, change to deleted
				dockerResponse.setStatusCode(DockerResponse.StatusCode.DELETED);
			}
		} catch (IOException e) {
			setDockerResponse(dockerResponse, e);
		} finally {
			httpURLConnection.disconnect();
		}
		return dockerResponse;
	}

	public DockerContainer getDockerContainer(String name) {
		DockerContainer dockerContainer = new DockerContainer();
		HttpURLConnection httpURLConnection = null;
		try {
			URL dockerVersionURL = new URL(dockerServer.toString() + "/containers/" + name + "/json?all=1");
			httpURLConnection = (HttpURLConnection) dockerVersionURL.openConnection();
			if (getDockerResponse(dockerContainer, httpURLConnection).equals(DockerResponse.StatusCode.OK)) {
				JSONObject jsonObject = readDockerResponseAsJSONObject(dockerContainer, httpURLConnection);
				if (jsonObject.has(DockerContainer.ID.toUpperCase())) {
					// 1.7 API has ID rather than Id
					dockerContainer.setId(jsonObject.getString(DockerContainer.ID.toUpperCase()));
				}
				if (jsonObject.has(DockerContainer.IMAGE)) {
					dockerContainer.setImage(jsonObject.getString(DockerContainer.IMAGE));
				}
				if (jsonObject.has(DockerContainer.COMMAND)) {
					dockerContainer.setCommand(jsonObject.getString(DockerContainer.COMMAND));
				} else if (jsonObject.has("Config")) {
					JSONObject config = jsonObject.getJSONObject("Config");
					JSONArray command = config.getJSONArray("Cmd");
					dockerContainer.setCommand(command.getString(0));
				}
				if (jsonObject.has(DockerContainer.CREATED)) {
					dockerContainer.setCreated(jsonObject.getString(DockerContainer.CREATED));
				}
				if (jsonObject.has(DockerContainer.STATUS)) {
					dockerContainer.setStatus(jsonObject.getString(DockerContainer.STATUS));
				} else if (jsonObject.has("State")) {
					JSONObject state = jsonObject.getJSONObject("State");
					Boolean running = state.getBoolean("Running");
					String startedAt = state.getString("StartedAt");
					String finishedAt = state.getString("FinishedAt");
					if (!running && finishedAt.endsWith("00Z")) {
						dockerContainer.setStatus("Created at " + dockerContainer.getCreated());
					} else if (running) {
						dockerContainer.setStatus("Started at " + startedAt);
					} else if (!running) {
						dockerContainer.setStatus("Stopped at " + finishedAt);
					}
				}
				if (jsonObject.has(DockerContainer.PORTS)) {
					dockerContainer.setPorts(jsonObject.getString(DockerContainer.PORTS));
				}
				if (jsonObject.has(DockerContainer.SIZE_RW)) {
					dockerContainer.setSize(jsonObject.getInt(DockerContainer.SIZE_RW));
				}
				if (jsonObject.has(DockerContainer.SIZE_ROOT_FS)) {
					dockerContainer.setSize(jsonObject.getInt(DockerContainer.SIZE_ROOT_FS));
				}
				if (jsonObject.has(DockerContainer.NAME)) {
					// strip the leading slash
					dockerContainer.setName(jsonObject.getString(DockerContainer.NAME).substring(1));
				}
			}
		} catch (IOException e) {
			setDockerResponse(dockerContainer, e);
		} catch (JSONException e) {
			setDockerResponse(dockerContainer, e);
		} finally {
			httpURLConnection.disconnect();
		}
		return dockerContainer;
	}

	public DockerContainers getDockerContainers() {
		DockerContainers dockerContainers = new DockerContainers();
		HttpURLConnection httpURLConnection = null;
		try {
			URL dockerVersionURL = new URL(dockerServer.toString() + "/containers/json?all=1");
			httpURLConnection = (HttpURLConnection) dockerVersionURL.openConnection();
			if (getDockerResponse(dockerContainers, httpURLConnection).equals(DockerResponse.StatusCode.OK)) {
				JSONArray jsonArray = readDockerResponseAsJSONArray(dockerContainers, httpURLConnection);
				for (int i = 0, isize = jsonArray.length(); i < isize; i++) {
					JSONObject jsonObject = jsonArray.getJSONObject(i);
					DockerContainer dockerContainer = new DockerContainer();
					if (jsonObject.has(DockerContainer.ID)) {
						dockerContainer.setId(jsonObject.getString(DockerContainer.ID));
					}
					if (jsonObject.has(DockerContainer.IMAGE)) {
						dockerContainer.setImage(jsonObject.getString(DockerContainer.IMAGE));
					}
					if (jsonObject.has(DockerContainer.COMMAND)) {
						dockerContainer.setCommand(jsonObject.getString(DockerContainer.COMMAND));
					}
					if (jsonObject.has(DockerContainer.CREATED)) {
						long time = jsonObject.getLong(DockerContainer.CREATED);
						Date date = new Date(time);
						Format format = new SimpleDateFormat();
						dockerContainer.setCreated(format.format(date).toString());
					}
					if (jsonObject.has(DockerContainer.STATUS)) {
						dockerContainer.setStatus(jsonObject.getString(DockerContainer.STATUS));
					}
					if (jsonObject.has(DockerContainer.PORTS)) {
						dockerContainer.setPorts(jsonObject.getString(DockerContainer.PORTS));
					}
					if (jsonObject.has(DockerContainer.SIZE_RW)) {
						dockerContainer.setSize(jsonObject.getInt(DockerContainer.SIZE_RW));
					}
					if (jsonObject.has(DockerContainer.SIZE_ROOT_FS)) {
						dockerContainer.setSize(jsonObject.getInt(DockerContainer.SIZE_ROOT_FS));
					}
					if (jsonObject.has(DockerContainer.NAMES)) {
						// names are in an array but there is only one name
						JSONArray names = jsonObject.getJSONArray(DockerContainer.NAMES);
						String name = names.getString(0);
						// strip the leading slash
						dockerContainer.setName(name.substring(1));
					}
					dockerContainers.addContainer(dockerContainer);
				}
			}
		} catch (IOException e) {
			setDockerResponse(dockerContainers, e);
		} catch (JSONException e) {
			setDockerResponse(dockerContainers, e);
		} finally {
			httpURLConnection.disconnect();
		}
		return dockerContainers;
	}

	public DockerImage getDockerImage(String repository) {
		DockerImage dockerImage = new DockerImage();
		HttpURLConnection httpURLConnection = null;
		try {
			URL dockerVersionURL = new URL(dockerServer.toString() + "/images/" + repository + "/json");
			httpURLConnection = (HttpURLConnection) dockerVersionURL.openConnection();
			if (getDockerResponse(dockerImage, httpURLConnection).equals(DockerResponse.StatusCode.OK)) {
				JSONObject jsonObject = readDockerResponseAsJSONObject(dockerImage, httpURLConnection);
				dockerImage.setRepository(repository);
				if (jsonObject.has(DockerImage.ID.toLowerCase())) {
					// 1.7 API has id rather than Id
					dockerImage.setId(jsonObject.getString(DockerImage.ID.toLowerCase()));
				}
				if (jsonObject.has(DockerImage.CREATED)) {
					dockerImage.setCreated(jsonObject.getString(DockerImage.CREATED));
				}
			} else if (dockerImage.getStatusCode().equals(DockerResponse.StatusCode.NO_SUCH_CONTAINER)) {
				// generic method returns 404 - no such container, change to no such image
				dockerImage.setStatusCode(DockerResponse.StatusCode.NO_SUCH_IMAGE);
			}
		} catch (IOException e) {
			setDockerResponse(dockerImage, e);
		} catch (JSONException e) {
			setDockerResponse(dockerImage, e);
		} finally {
			httpURLConnection.disconnect();
		}
		return dockerImage;
	}

	public DockerImages getDockerImages() {
		DockerImages dockerImages = new DockerImages();
		HttpURLConnection httpURLConnection = null;
		try {
			URL dockerVersionURL = new URL(dockerServer.toString() + "/images/json");
			httpURLConnection = (HttpURLConnection) dockerVersionURL.openConnection();
			if (getDockerResponse(dockerImages, httpURLConnection).equals(DockerResponse.StatusCode.OK)) {
				JSONArray jsonArray = readDockerResponseAsJSONArray(dockerImages, httpURLConnection);
				for (int i = 0, isize = jsonArray.length(); i < isize; i++) {
					JSONObject jsonObject = jsonArray.getJSONObject(i);
					DockerImage dockerImage = new DockerImage();
					if (jsonObject.has(DockerImage.ID)) {
						dockerImage.setId(jsonObject.getString(DockerImage.ID));
					}
					if (jsonObject.has(DockerImage.CREATED)) {
						long time = jsonObject.getLong(DockerImage.CREATED);
						Date date = new Date(time);
						Format format = new SimpleDateFormat();
						dockerImage.setCreated(format.format(date).toString());
					}
					if (jsonObject.has(DockerImage.SIZE)) {
						dockerImage.setSize(jsonObject.getLong(DockerImage.SIZE));
					}
					if (jsonObject.has(DockerImage.VIRTUAL_SIZE)) {
						dockerImage.setVirtualSize(jsonObject.getLong(DockerImage.VIRTUAL_SIZE));
					}
					if (jsonObject.has(DockerImage.REPOTAGS)) {
						JSONArray repotags = jsonObject.getJSONArray(DockerImage.REPOTAGS);
						for (int j = 0, jsize = repotags.length(); j < jsize; j++) {
							String[] repoTag = repotags.getString(j).split(":");
							dockerImage.setRepository(repoTag[0]);
							dockerImage.addTag(repoTag[1]);
						}
					}
					dockerImages.addImage(dockerImage);
				}
			}
		} catch (IOException e) {
			setDockerResponse(dockerImages, e);
		} catch (JSONException e) {
			setDockerResponse(dockerImages, e);
		} finally {
			httpURLConnection.disconnect();
		}
		return dockerImages;
	}

	private DockerResponse.StatusCode getDockerResponse(DockerResponse dockerResponse, HttpURLConnection httpURLConnection) {
		try {
			int responseCode = httpURLConnection.getResponseCode();
			if (responseCode == HttpURLConnection.HTTP_OK) {
				dockerResponse.setStatusCode(DockerResponse.StatusCode.OK);
				return DockerResponse.StatusCode.OK;
			} else if (responseCode == HttpURLConnection.HTTP_CREATED) {
				dockerResponse.setStatusCode(DockerResponse.StatusCode.CREATED);
				return DockerResponse.StatusCode.CREATED;
			} else if (responseCode == HttpURLConnection.HTTP_NO_CONTENT) {
				dockerResponse.setStatusCode(DockerResponse.StatusCode.STARTED);
				return DockerResponse.StatusCode.STARTED;
			} else if (responseCode == HttpURLConnection.HTTP_BAD_REQUEST) {
				dockerResponse.setStatusCode(DockerResponse.StatusCode.BAD_PARAMETER);
				dockerResponse.setStatusMessage(httpURLConnection.getResponseMessage());
				return DockerResponse.StatusCode.BAD_PARAMETER;
			} else if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
				dockerResponse.setStatusCode(DockerResponse.StatusCode.NO_SUCH_CONTAINER);
				dockerResponse.setStatusMessage(httpURLConnection.getResponseMessage());
				return DockerResponse.StatusCode.NO_SUCH_CONTAINER;
			} else if (responseCode == HttpURLConnection.HTTP_INTERNAL_ERROR) {
				dockerResponse.setStatusCode(DockerResponse.StatusCode.SERVER_ERROR);
				dockerResponse.setStatusMessage(httpURLConnection.getResponseMessage());
				return DockerResponse.StatusCode.SERVER_ERROR;
			} else {
				throw new RuntimeException("Unknown status code :" + responseCode);
			}
		} catch (IOException e) {
			setDockerResponse(dockerResponse, e);
		}
		return DockerResponse.StatusCode.SERVER_ERROR;
	}

	public URI getDockerServer() {
		return dockerServer;
	}

	/**
	 * Get the Docker version.
	 * 
	 * @return the docker version.
	 */
	public DockerVersion getDockerVersion() {
		DockerVersion dockerVersion = new DockerVersion();
		HttpURLConnection httpURLConnection = null;
		try {
			URL dockerVersionURL = new URL(dockerServer.toString() + "/version");
			httpURLConnection = (HttpURLConnection) dockerVersionURL.openConnection();
			if (getDockerResponse(dockerVersion, httpURLConnection).equals(DockerResponse.StatusCode.OK)) {
				JSONObject jsonObject = readDockerResponseAsJSONObject(dockerVersion, httpURLConnection);
				if (jsonObject.has(DockerVersion.VERSION)) {
					dockerVersion.setVersion(jsonObject.getString(DockerVersion.VERSION));
				}
				if (jsonObject.has(DockerVersion.GIT_COMMIT)) {
					dockerVersion.setGitCommit(jsonObject.getString(DockerVersion.GIT_COMMIT));
				}
				if (jsonObject.has(DockerVersion.GO_VERSION)) {
					dockerVersion.setGoVersion(jsonObject.getString(DockerVersion.GO_VERSION));
				}
			}
		} catch (IOException e) {
			dockerVersion.setStatusCode(DockerResponse.StatusCode.SERVER_ERROR);
			dockerVersion.setStatusMessage(e.getLocalizedMessage());
			Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.config"); //$NON-NLS-1$
			logger.error(e.getLocalizedMessage(), e); //$NON-NLS-1$
		} catch (JSONException e) {
			dockerVersion.setStatusCode(DockerResponse.StatusCode.SERVER_ERROR);
			dockerVersion.setStatusMessage(e.getLocalizedMessage());
			Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.config"); //$NON-NLS-1$
			logger.error(e.getLocalizedMessage(), e); //$NON-NLS-1$
		} finally {
			httpURLConnection.disconnect();
		}
		return dockerVersion;
	}

	private JSONArray readDockerResponseAsJSONArray(DockerResponse dockerResponse, HttpURLConnection httpURLConnection) {
		try {
			String dockerResponseAsString = readDockerResponseAsString(dockerResponse, httpURLConnection);
			JSONArray jsonArray = new JSONArray(dockerResponseAsString);
			return jsonArray;
		} catch (JSONException e) {
			dockerResponse.setStatusCode(DockerResponse.StatusCode.SERVER_ERROR);
			dockerResponse.setStatusMessage(e.getLocalizedMessage());
			Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.config"); //$NON-NLS-1$
			logger.error(e.getLocalizedMessage(), e); //$NON-NLS-1$
		}
		return new JSONArray();
	}

	private JSONObject readDockerResponseAsJSONObject(DockerResponse dockerResponse, HttpURLConnection httpURLConnection) {
		try {
			String dockerResponseAsString = readDockerResponseAsString(dockerResponse, httpURLConnection);
			JSONObject jsonObject = new JSONObject(dockerResponseAsString);
			return jsonObject;
		} catch (JSONException e) {
			dockerResponse.setStatusCode(DockerResponse.StatusCode.SERVER_ERROR);
			dockerResponse.setStatusMessage(e.getLocalizedMessage());
			Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.config"); //$NON-NLS-1$
			logger.error(e.getLocalizedMessage(), e); //$NON-NLS-1$
		}
		return new JSONObject();
	}

	private String readDockerResponseAsString(DockerResponse dockerResponse, HttpURLConnection httpURLConnection) {
		try {
			InputStream inputStream = httpURLConnection.getInputStream();
			char[] chars = new char[1024];
			Charset utf8 = Charset.forName("UTF-8");
			InputStreamReader inputStreamReader = new InputStreamReader(inputStream, utf8);
			StringBuilder stringBuilder = new StringBuilder();
			int count;
			while ((count = inputStreamReader.read(chars, 0, chars.length)) != -1) {
				stringBuilder.append(chars, 0, count);
			}
			inputStreamReader.close();
			return stringBuilder.toString();
		} catch (IOException e) {
			dockerResponse.setStatusCode(DockerResponse.StatusCode.SERVER_ERROR);
			dockerResponse.setStatusMessage(e.getLocalizedMessage());
			Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.config"); //$NON-NLS-1$
			logger.error(e.getLocalizedMessage(), e); //$NON-NLS-1$
		}
		return "{}";
	}

	private void setDockerResponse(DockerResponse dockerResponse, Exception e) {
		dockerResponse.setStatusCode(DockerResponse.StatusCode.SERVER_ERROR);
		dockerResponse.setStatusMessage(e.getLocalizedMessage());
		Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.config"); //$NON-NLS-1$
		logger.error(e.getLocalizedMessage(), e); //$NON-NLS-1$
	}

	public DockerContainer startDockerContainer(String containerId) {
		DockerContainer dockerContainer = new DockerContainer();
		HttpURLConnection httpURLConnection = null;
		try {
			URL dockerStartURL = new URL(dockerServer.toString() + "/containers/" + containerId + "/start");
			httpURLConnection = (HttpURLConnection) dockerStartURL.openConnection();
			httpURLConnection.setRequestMethod("POST");
			httpURLConnection.connect();

			if (getDockerResponse(dockerContainer, httpURLConnection).equals(DockerResponse.StatusCode.STARTED)) {
				// return the docker container with updated status information.
				dockerContainer = getDockerContainer(containerId);
				dockerContainer.setStatusCode(DockerResponse.StatusCode.STARTED);
			}
		} catch (IOException e) {
			setDockerResponse(dockerContainer, e);
		} finally {
			httpURLConnection.disconnect();
		}
		return dockerContainer;
	}

	public DockerContainer stopDockerContainer(String containerId) {
		DockerContainer dockerContainer = new DockerContainer();
		HttpURLConnection httpURLConnection = null;
		try {
			URL dockerStartURL = new URL(dockerServer.toString() + "/containers/" + containerId + "/stop");
			httpURLConnection = (HttpURLConnection) dockerStartURL.openConnection();
			httpURLConnection.setRequestMethod("POST");
			httpURLConnection.connect();

			if (getDockerResponse(dockerContainer, httpURLConnection).equals(DockerResponse.StatusCode.STARTED)) {
				// return the docker container with updated status information.
				dockerContainer = getDockerContainer(containerId);
				// generic method returns 204 - no error, change to stopped
				dockerContainer.setStatusCode(DockerResponse.StatusCode.STOPPED);
			}
		} catch (IOException e) {
			setDockerResponse(dockerContainer, e);
		} finally {
			httpURLConnection.disconnect();
		}
		return dockerContainer;
	}
}
