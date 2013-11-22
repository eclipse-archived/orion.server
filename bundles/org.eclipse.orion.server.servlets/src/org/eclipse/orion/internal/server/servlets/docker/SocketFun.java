package org.eclipse.orion.internal.server.servlets.docker;

import java.io.*;
import java.net.*;
import org.json.*;

public class SocketFun {

	static String DOCKER_LIST_CONTAINERS = "http://127.0.0.1:4243/containers/json?all=1";
	static String DOCKER_INSPECT_CONTAINERS = "http://127.0.0.1:4243/containers/bog/json";
	static String DOCKER_CREATE_CONTAINERS = "http://127.0.0.1:4243/containers/create?name=bogAwesome";

	private static byte[] readStream(InputStream in) throws IOException {
		byte[] buf = new byte[1024];
		int count = 0;
		ByteArrayOutputStream out = new ByteArrayOutputStream(1024);
		while ((count = in.read(buf)) != -1)
			out.write(buf, 0, count);
		return out.toByteArray();
	}

	public static void listContainers() {
		URL url;
		HttpURLConnection conn = null;
		try {
			url = new URL(DOCKER_LIST_CONTAINERS);
			conn = (HttpURLConnection) url.openConnection();
			BufferedInputStream in = new BufferedInputStream(conn.getInputStream());
			byte[] body = readStream(in);
			int http_status = conn.getResponseCode();
			// conn.getResponseCode(), conn.getHeaderFields(), body);
			String resopinse = new String(body);
			System.out.println(resopinse);
		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			if (conn != null)
				conn.disconnect();
		}
	}

	public static void inspectContainers() {
		URL url;
		HttpURLConnection conn = null;
		try {
			url = new URL(DOCKER_INSPECT_CONTAINERS);
			conn = (HttpURLConnection) url.openConnection();
			// conn.addRequestProperty(R, value);
			BufferedInputStream in = new BufferedInputStream(conn.getInputStream());
			byte[] body = readStream(in);
			int http_status = conn.getResponseCode();
			// conn.getResponseCode(), conn.getHeaderFields(), body);
			String resopinse = new String(body);
			System.out.println(resopinse);
		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			if (conn != null)
				conn.disconnect();
		}
	}

	public static void createContainers() {
		HttpURLConnection httpcon = null;
		try {
			JSONObject json = new JSONObject();
			json.put("Hostname", "");
			json.put("User", "");
			json.put("Memory", 0);
			json.put("MemorySwap", 0);
			json.put("AttachStdin", false);
			json.put("AttachStdout", false);
			json.put("AttachStderr", false);
			json.put("PortSpecs", JSONObject.NULL);
			json.put("Privileged", false);
			json.put("Tty", false);
			json.put("OpenStdin", false);
			json.put("StdinOnce", false);
			json.put("Env", JSONObject.NULL);

			JSONArray cmdArray = new JSONArray();
			cmdArray.put("/bin/bash");
			json.put("Cmd", cmdArray);

			json.put("Dns", JSONObject.NULL);
			json.put("Image", "ubuntu");

			JSONObject volume = new JSONObject();
			json.put("Volumes", volume);
			json.put("VolumesFrom", "");
			json.put("WorkingDir", "");

			byte[] outputBytes = json.toString().getBytes("UTF-8");

			URL url = new URL(DOCKER_CREATE_CONTAINERS);
			httpcon = (HttpURLConnection) url.openConnection();
			httpcon.setDoOutput(true);
			httpcon.setRequestProperty("Content-Type", "application/json");
			httpcon.setRequestProperty("Accept", "application/json");
			httpcon.setRequestMethod("POST");
			httpcon.connect();

			OutputStream os = httpcon.getOutputStream();
			os.write(outputBytes);
			BufferedInputStream in = new BufferedInputStream(httpcon.getInputStream());
			byte[] body = readStream(in);
			String resopinse = new String(body);
			System.out.println(resopinse);
			os.close();
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			if (httpcon != null)
				httpcon.disconnect();
		}
	}

	public static void main(String[] args) {
		//listContainers();
		// inspectContainers();
		// createContainers();
	}
}
