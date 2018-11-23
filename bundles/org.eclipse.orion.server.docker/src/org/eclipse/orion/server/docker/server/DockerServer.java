/*******************************************************************************
 * Copyright (c) 2013, 2014 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.docker.server;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility to handle requests to and from the Docker Remote API.
 * 
 * @author Anthony Hunter
 */
public class DockerServer {

	private List<String> containerConnections;

	private URI dockerServer;

	private URI dockerProxy;
	
	private String groupId;

	private String portStart;

	private String portEnd;

	private List<String> portsUsed;

	private String userId;
	
	private final Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.docker"); //$NON-NLS-1$
	
	public DockerServer(URI dockerServer, URI dockerProxy, String portStart, String portEnd, String userId, String groupId) {
		super();
		this.dockerServer = dockerServer;
		this.dockerProxy = dockerProxy;
		this.portStart = portStart;
		this.portEnd = portEnd;
		this.userId = userId;
		this.groupId = groupId;
		this.containerConnections = new ArrayList<String>();
	}

	public DockerResponse attachDockerContainer(String containerId, String originURL) {
		DockerResponse dockerResponse = new DockerResponse();
		try {
			String wsServer = dockerProxy.toString().replaceFirst("http", "ws");
			if (dockerProxy.toString().startsWith("https")) {
				wsServer = dockerProxy.toString().replaceFirst("https", "ws");
			}
			URI dockerAttachURI = new URI(wsServer + "/containers/" + containerId + "/attach/ws?stream=1&stdin=1&stdout=1&stderr=1");
			containerConnections.add(containerId);
			dockerResponse.setStatusCode(DockerResponse.StatusCode.ATTACHED);
			// put the URI in the response message
			dockerResponse.setStatusMessage(dockerAttachURI.toString());
		} catch (URISyntaxException e) {
			setDockerResponse(dockerResponse, e);
		} catch (Exception e) {
			setDockerResponse(dockerResponse, e);
		}

		return dockerResponse;
	}

	public DockerContainer createDockerContainer(String imageName, String userName, String volume) {
		DockerContainer dockerContainer = new DockerContainer();
		HttpURLConnection httpURLConnection = null;
		try {
			JSONObject requestJSONObject = new JSONObject();
			JSONArray cmdArray = new JSONArray();
			cmdArray.put("bash");
			requestJSONObject.put("Cmd", cmdArray);
			requestJSONObject.put("Image", imageName);
			requestJSONObject.put("User", userName);
			requestJSONObject.put("AttachStdin", true);
			requestJSONObject.put("AttachStdout", true);
			requestJSONObject.put("AttachStderr", true);
			requestJSONObject.put("Tty", true);
			requestJSONObject.put("OpenStdin", true);
			requestJSONObject.put("StdinOnce", true);
			if (volume != null) {
				// volumes in create are in the format :  "Volumes": { "/home/user/project": {} }
				JSONObject volumesObject = new JSONObject();
				String orionVolume = volume.substring(volume.indexOf(':') + 1, volume.lastIndexOf(':'));
				volumesObject.put(orionVolume, new JSONObject());
				requestJSONObject.put("Volumes", volumesObject);
			}
			byte[] outputBytes = requestJSONObject.toString().getBytes("UTF-8");

			URL dockerCreateContainerURL = new URL(dockerServer.toString() + "/containers/create?name=" + userName);
			httpURLConnection = (HttpURLConnection) dockerCreateContainerURL.openConnection();
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

	public DockerImage createDockerOrionBaseImage() {
		DockerImage dockerImage = new DockerImage();
		HttpURLConnection httpURLConnection = null;
		try {
			URL createDockerOrionBaseImageURL = new URL(dockerServer.toString() + "/images/create?fromSrc&t=orion-base");
			httpURLConnection = (HttpURLConnection) createDockerOrionBaseImageURL.openConnection();
			httpURLConnection.setDoOutput(true);
			httpURLConnection.setRequestProperty("Content-Type", "application/tar");
			httpURLConnection.setRequestProperty("Accept", "application/json");
			httpURLConnection.setRequestMethod("POST");
			httpURLConnection.connect();

			String orionBaseTar = "/workspace/orion-dev/git/org.eclipse.orion.server/bundles/org.eclipse.orion.server.servlets/src/org/eclipse/orion/internal/server/servlets/docker/orion-base.tar";
			File orionBaseTarFile = new File(orionBaseTar);
			PrintWriter writer = new PrintWriter(new OutputStreamWriter(httpURLConnection.getOutputStream(), "UTF-8"));
			BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(orionBaseTarFile)));
			for (String line; (line = reader.readLine()) != null;) {
				writer.print(line);
			}

			if (getDockerResponse(dockerImage, httpURLConnection).equals(DockerResponse.StatusCode.OK)) {
				JSONObject jsonObject = readDockerResponseAsJSONObject(dockerImage, httpURLConnection);
				String id = jsonObject.getString(DockerContainer.ID);
				dockerImage = getDockerImage(id);
				dockerImage.setStatusCode(DockerResponse.StatusCode.CREATED);
			}
			reader.close();
			writer.close();
		} catch (IOException e) {
			setDockerResponse(dockerImage, e);
		} catch (JSONException e) {
			setDockerResponse(dockerImage, e);
		} finally {
			httpURLConnection.disconnect();
		}
		return dockerImage;
	}

	public DockerImage createDockerUserBaseImage(String userName) {
		DockerImage dockerImage = new DockerImage();
		HttpURLConnection httpURLConnection = null;
		try {
			String userBase = userName + "-base";
			URL createDockerUserBaseImageURL = new URL(dockerServer.toString() + "/build?t=" + userBase + "&rm=true");
			httpURLConnection = (HttpURLConnection) createDockerUserBaseImageURL.openConnection();
			httpURLConnection.setDoOutput(true);
			httpURLConnection.setRequestProperty("Content-Type", "application/tar");
			httpURLConnection.setRequestProperty("Accept", "application/json");
			httpURLConnection.setRequestMethod("POST");
			httpURLConnection.connect();

			DockerFile userDockerFile = new DockerFile(userName, userId, groupId);
			File userBaseTarFile = userDockerFile.getTarFile();

			FileInputStream fileInputStream = new FileInputStream(userBaseTarFile);
			OutputStream outputStream = httpURLConnection.getOutputStream();
			byte[] buffer = new byte[4096];
			int bytes_read;
			while ((bytes_read = fileInputStream.read(buffer)) != -1) {
				outputStream.write(buffer, 0, bytes_read);
			}
			fileInputStream.close();

			if (getDockerResponse(dockerImage, httpURLConnection).equals(DockerResponse.StatusCode.OK)) {
				String responseString = readDockerResponseAsString(dockerImage, httpURLConnection);
				if (responseString.indexOf("Successfully built") == -1) {
					dockerImage.setStatusCode(DockerResponse.StatusCode.SERVER_ERROR);
					dockerImage.setStatusMessage(responseString);
				} else {
					dockerImage = getDockerImage(userBase);
					dockerImage.setStatusCode(DockerResponse.StatusCode.CREATED);
				}
			}

			userDockerFile.cleanup();
		} catch (IOException e) {
			setDockerResponse(dockerImage, e);
		} finally {
			httpURLConnection.disconnect();
		}
		return dockerImage;
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

	public void detachDockerContainer(String user) {
		if (containerConnections.contains(user)) {
			containerConnections.remove(user);
		}
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
				if (jsonObject.has("NetworkSettings")) {
					JSONObject networkSettings = jsonObject.getJSONObject("NetworkSettings");
					if (networkSettings.has(DockerContainer.PORTS)) {
						dockerContainer.setPorts(networkSettings.getString(DockerContainer.PORTS));
					}
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
				if (jsonObject.has(DockerImage.CONFIG)) {
					JSONObject config = jsonObject.getJSONObject(DockerImage.CONFIG);
					if (config.has(DockerImage.EXPOSED_PORTS)) {
						JSONObject exposedPorts = config.getJSONObject(DockerImage.EXPOSED_PORTS);
						Iterator<?> iterator = exposedPorts.keys();
						while (iterator.hasNext()) {
							String port = (String) iterator.next();
							dockerImage.addPort(port);
						}
					}
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
						// Create a separate image for each tag in the repository
						JSONArray repotags = jsonObject.getJSONArray(DockerImage.REPOTAGS);
						for (int j = 0, jsize = repotags.length(); j < jsize; j++) {
							String[] repoTag = repotags.getString(j).split(":");
							if (j == 0) {
								// first tag
								dockerImage.setRepository(repoTag[0]);
								dockerImage.setTag(repoTag[1]);
							} else {
								// second and other tags
								DockerImage nextDockerImage = new DockerImage();
								nextDockerImage.setId(dockerImage.getId());
								nextDockerImage.setCreated(dockerImage.getCreated());
								nextDockerImage.setSize(dockerImage.getSize());
								nextDockerImage.setRepository(dockerImage.getRepository());
								nextDockerImage.setTag(repoTag[1]);
								dockerImages.addImage(nextDockerImage);
							}
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
			if (e instanceof ConnectException && e.getLocalizedMessage().contains("Connection refused")) {
				// connection refused means the docker server is not running.
				dockerResponse.setStatusCode(DockerResponse.StatusCode.CONNECTION_REFUSED);
				return DockerResponse.StatusCode.CONNECTION_REFUSED;
			}
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
			setDockerResponse(dockerVersion, e);
		} catch (JSONException e) {
			setDockerResponse(dockerVersion, e);
		} finally {
			httpURLConnection.disconnect();
		}
		return dockerVersion;
	}

	private void initPortsUsed() {
		try {
			// use our docker version to make sure that docker is running 
			DockerVersion dockerVersion = getDockerVersion();
			if (dockerVersion.getStatusCode() != DockerResponse.StatusCode.OK) {
				// just return
				return;
			}
			DockerContainers dockerContainers = getDockerContainers();
			switch (dockerContainers.getStatusCode()) {
				case SERVER_ERROR :
				case CONNECTION_REFUSED :
					logger.error(dockerContainers.getStatusMessage());
					return;
				case OK :
					portsUsed = new ArrayList<String>();
					for (DockerContainer dockerContainer : dockerContainers.getContainers()) {
						if (dockerContainer.getPorts() != null) {
							JSONArray portsArray = new JSONArray(dockerContainer.getPorts());
							for (int i = 0; i < portsArray.length(); i++) {
								JSONObject portsObject = portsArray.getJSONObject(i);
								if (portsObject.has("PublicPort")) {
									int portValue = portsObject.getInt("PublicPort");
									portsUsed.add(Integer.toString(portValue));
								}
							}
						}
					}
					Collections.sort(portsUsed);
					if (logger.isDebugEnabled()) {
						logger.debug("Docker Server has mapped " + portsUsed.size() + " ports already");
						if (portsUsed.size() > 0) {
							StringBuffer portsUsedList = new StringBuffer("Docker Server ports mapped: ");
							for (String port : portsUsed) {
								portsUsedList.append(port);
								portsUsedList.append(" ");
							}
							logger.debug(portsUsedList.toString());
						}
					}
					return;
				default :
					return;
			}
		} catch (JSONException e) {
			logger.error(e.getLocalizedMessage(), e);
		}
	}

	public boolean isAttachedDockerContainer(String user) {
		return containerConnections.contains(user);
	}

	private JSONArray readDockerResponseAsJSONArray(DockerResponse dockerResponse, HttpURLConnection httpURLConnection) {
		try {
			String dockerResponseAsString = readDockerResponseAsString(dockerResponse, httpURLConnection);
			JSONArray jsonArray = new JSONArray(dockerResponseAsString);
			return jsonArray;
		} catch (JSONException e) {
			setDockerResponse(dockerResponse, e);
		}
		return new JSONArray();
	}

	private JSONObject readDockerResponseAsJSONObject(DockerResponse dockerResponse, HttpURLConnection httpURLConnection) {
		try {
			String dockerResponseAsString = readDockerResponseAsString(dockerResponse, httpURLConnection);
			JSONObject jsonObject = new JSONObject(dockerResponseAsString);
			return jsonObject;
		} catch (JSONException e) {
			setDockerResponse(dockerResponse, e);
		}
		return new JSONObject();
	}

	private String readDockerResponseAsString(DockerResponse dockerResponse, HttpURLConnection httpURLConnection) {
		try {
			InputStream inputStream = httpURLConnection.getInputStream();
			Charset utf8 = Charset.forName("UTF-8");
			BufferedReader rd = new BufferedReader(new InputStreamReader(inputStream, utf8));
			StringBuilder stringBuilder = new StringBuilder();
			String line;
			while ((line = rd.readLine()) != null) {
				stringBuilder.append(line);
			}
			return stringBuilder.toString();
		} catch (IOException e) {
			setDockerResponse(dockerResponse, e);
		}
		return "{}";
	}

	private void setDockerResponse(DockerResponse dockerResponse, Exception e) {
		dockerResponse.setStatusCode(DockerResponse.StatusCode.SERVER_ERROR);
		dockerResponse.setStatusMessage(e.getLocalizedMessage());
		logger.error(e.getLocalizedMessage(), e);
	}

	public DockerContainer startDockerContainer(String containerId, String volume, List<String> portBindings) {
		DockerContainer dockerContainer = new DockerContainer();
		HttpURLConnection httpURLConnection = null;
		try {
			JSONObject requestJSONObject = new JSONObject();
			if (volume != null) {
				// binds are in the format : "Binds": [ "/host/path/serverworkspace/us/user/OrionContent/project:/OrionContent/project:rw" ]
				JSONArray bindsArray = new JSONArray();
				bindsArray.put(volume);
				requestJSONObject.put("Binds", bindsArray);
			}
			if (portBindings != null && !portBindings.isEmpty()) {
				// PortBindings are in the format : {"PortBindings": { "22/tcp": [{ "HostPort": "11022" }]} }
				JSONObject portsObject = new JSONObject();
				for (String port : portBindings) {
					String nextAvailablePort = getNextAvailablePort();
					if (nextAvailablePort != null) {
						JSONObject hostPort = new JSONObject();
						hostPort.put("HostPort", nextAvailablePort);
						JSONArray hostArray = new JSONArray();
						hostArray.put(hostPort);
						portsObject.put(port + "/tcp", hostArray);
					} else {
						// nextAvailablePort is null, do not assign any bindings
						break;
					}
				}
				requestJSONObject.put("PortBindings", portsObject);
			}
			byte[] outputBytes = requestJSONObject.toString().getBytes("UTF-8");

			URL dockerStartURL = new URL(dockerServer.toString() + "/containers/" + containerId + "/start");
			httpURLConnection = (HttpURLConnection) dockerStartURL.openConnection();
			httpURLConnection.setDoOutput(true);
			httpURLConnection.setRequestProperty("Content-Type", "application/json");
			httpURLConnection.setRequestProperty("Accept", "application/json");
			httpURLConnection.setRequestMethod("POST");
			httpURLConnection.connect();
			OutputStream outputStream = httpURLConnection.getOutputStream();
			outputStream.write(outputBytes);

			if (getDockerResponse(dockerContainer, httpURLConnection).equals(DockerResponse.StatusCode.STARTED)) {
				// return the docker container with updated status information.
				dockerContainer = getDockerContainer(containerId);
				dockerContainer.setStatusCode(DockerResponse.StatusCode.STARTED);
			} else if (dockerContainer.getStatusCode().equals(DockerResponse.StatusCode.SERVER_ERROR)) {
				// handle HTTP Error: statusCode=500 start: Cannot start container: The container is already running.
				DockerContainer newDockerContainer = getDockerContainer(containerId);
				if (newDockerContainer.getStatus().startsWith("Started at")) {
					dockerContainer.setStatusCode(DockerResponse.StatusCode.RUNNING);
					dockerContainer.setStatusMessage(newDockerContainer.getStatusMessage());
				}
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

	private String getNextAvailablePort() {
		if (portStart == null || portEnd == null) {
			return null;
		}
		if (portsUsed == null) {
			// initialize the mapped ports that are already used
			initPortsUsed();
			if (portsUsed == null) {
				// the list of mapped ports is still null, there is a problem logged already
				return null;
			}
		}
		String nextAvailablePort = portStart;
		while (portsUsed.contains(nextAvailablePort)) {
			int nextPort = Integer.parseInt(nextAvailablePort) + 1;
			nextAvailablePort = Integer.toString(nextPort);
			if (portEnd.equals(nextAvailablePort)) {
				logger.error("Docker Server: no free ports, used " + nextAvailablePort);
				return null;
			}
		}
		portsUsed.add(nextAvailablePort);
		return nextAvailablePort;
	}

	public String getPortStart() {
		return portStart;
	}

	public String getPortEnd() {
		return portEnd;
	}

	public DockerContainer stopDockerContainer(String containerId) {
		DockerContainer dockerContainer = new DockerContainer();
		HttpURLConnection httpURLConnection = null;
		try {
			URL dockerStartURL = new URL(dockerServer.toString() + "/containers/" + containerId + "/stop?t=0");
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
