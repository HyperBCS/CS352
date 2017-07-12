import java.net.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.io.*;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.util.Locale;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PartialHTTP1Server {

	private ServerSocket serverSocket; // the main socket for the server
	private int port; // which port it exists on gotten from command line
	private static final Logger LOGGER = Logger.getLogger(PartialHTTP1Server.class.getName());

	/**
	 * Constructor to start the server and creates a new thread for each connection
	 */
	PartialHTTP1Server(String[] args) {

		// Initialize a serverSocket to accept clients
		try {
			this.port = Integer.parseInt(args[0]);
			this.serverSocket = new ServerSocket(port);

		} catch (Exception e) {
			String error = getStackTrace(e);
			LOGGER.log(Level.SEVERE, error);
		}
	}

	public static String getStackTrace(final Throwable throwable) {
		final StringWriter sw = new StringWriter();
		final PrintWriter pw = new PrintWriter(sw, true);
		throwable.printStackTrace(pw);
		return sw.getBuffer().toString();
	}

	public void start() {
		if (serverSocket != null && !serverSocket.isClosed()) {
			String usage = "HTTP 1.0 server listening on port " + port;
			LOGGER.log(Level.INFO, usage);
			while (serverSocket.isBound()) {
				try { // Accept the client
					Socket client = serverSocket.accept();
					client.setSoTimeout(3000); // set timeout to 3000
					HTTPThread clientThread = new HTTPThread(client);
					Thread t = new Thread(clientThread);
					t.start();
				} catch (Exception e) { // When we catch the error, print it out
					String error = getStackTrace(e);
					LOGGER.log(Level.SEVERE, error);
				}
			}

		}
	}

	/*
	 * Instantiates the server class by passing in input array
	 */
	public static void main(String[] args) {
		// Check that our arguments are correct. If not we print message and
		// exit.
		if (args.length != 1) {
			LOGGER.log(Level.INFO, "Usage: java -cp . PartialHTTP1Server 3456");
			return;
		}
		System.setProperty("java.util.logging.SimpleFormatter.format",
				"[%1$tm/%1$td/%1$tY %1$tH:%1$tM:%1$tS] %5$s%6$s%n");
		PartialHTTP1Server server = new PartialHTTP1Server(args);
		server.start();
		return;
	}

	/**
	 * Thread to handle each HTTP request
	 */
	class HTTPThread implements Runnable {
		private Socket clientSocket;
		private String reqStr;

		HTTPThread(Socket c) {
			this.clientSocket = c;
		}

		/**
		 * Add appropriate response depdning on the status code
		 */
		private String codeString(int status) {
			switch (status) {
			case 200:
				return "HTTP/1.0 200 OK";

			case 304:
				return "HTTP/1.0 304 Not Modified";

			case 400:
				return "HTTP/1.0 400 Bad Request";

			case 403:
				return "HTTP/1.0 403 Forbidden";

			case 404:
				return "HTTP/1.0 404 Not Found";

			case 408:
				return "HTTP/1.0 408 Request Timeout";

			case 500:
				return "HTTP/1.0 500 Internal Server Error";

			case 501:
				return "HTTP/1.0 501 Not Implemented";

			case 503:
				return "HTTP/1.0 503 Service Unavailable";

			case 505:
				return "HTTP/1.0 505 HTTP Version Not Supported";

			default:
				return "HTTP/1.0 200 OK";
			}
		}

		/**
		 * Log exactly when the request was made with date and time
		 */
		private String logBuilder(int status) {
			StringBuilder pre = new StringBuilder();
			String addr = clientSocket.getInetAddress().getHostName();
			int clientPort = clientSocket.getPort();
			pre.append(addr + ":" + clientPort + " - ");
			pre.append(reqStr + " - ");
			pre.append(status);
			return pre.toString();
		}

		/**
		 * @param req-
		 *            the request inputted by the user to be parsed
		 * @return a ReqObj containing the method and path to resource This method will
		 *         parse a request string and return a request object
		 **/
		private ReqObj parseReq(String req) {
			// Make sure request isnt null
			if (req == null) {
				return null;
			}
			String[] reqArr = req.split(" ");
			String method;
			float ver = 0;
			// Make sure that method request is formatted correctly
			if (reqArr.length == 3 && reqArr[0].length() > 0 && reqArr[0].chars().allMatch(Character::isLetter)
					&& reqArr[0].toUpperCase().equals(reqArr[0])) {
				// set method
				method = reqArr[0];
			} else {
				return null;

			}

			if (reqArr[2].length() > 0) {
				String[] verArr = reqArr[2].split("/");
				if (verArr.length != 2) {
					return null;
				}
				if (verArr[0].equals("HTTP") && verArr[1].split("\\.").length == 2) {
					try {
						ver = Float.parseFloat(verArr[1]);
					} catch (Exception e) {
						return null;
					}
				} else {
					return null;
				}
			}
			// Set paths to be merged
			if (reqArr[1].length() > 0 && reqArr[1].charAt(0) == '/') {
				try {
					String relativePath = java.net.URLDecoder.decode(reqArr[1], "UTF-8");
					String workingDir = System.getProperty("user.dir");
					File dir = new File(workingDir);
					File fullPath = new File(dir, relativePath);
					return new ReqObj(method, fullPath, ver);
				} catch (Exception e) {
					String error = getStackTrace(e);
					LOGGER.log(Level.SEVERE, error);
					return null;
				}

			} else { // bad path request
				return null;
			}

		}

		private Date parseDate(String input) {
			String inputDate = "";
			if (input.charAt(0) == ' ') {
				inputDate = input.substring(1);
			}
			SimpleDateFormat parser = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
			parser.setTimeZone(TimeZone.getTimeZone("GMT"));
			try {
				return parser.parse(inputDate);

			} catch (ParseException e) {
				String error = getStackTrace(e);
				LOGGER.log(Level.SEVERE, error);
				return null;
			}
		}

		private void parseHeaderParam(String[] headerParts, ReqObj req) {
			if (headerParts[0].equalsIgnoreCase("if-modified-since")) {
				Date ifModified = parseDate(headerParts[1]);
				if (ifModified != null) {
					req.setIfModified(true);
					req.setIfModifiedDate(ifModified);
				}
			}
		}

		private void parseHeader(List<String> header, ReqObj req) {
			for (String headerStr : header) {
				if (headerStr != null && headerStr.length() > 0) {
					String[] headerParts = headerStr.split(":", 2);
					if (headerParts.length == 2 && headerParts[0].length() > 0 && headerParts[1].length() > 0) {
						parseHeaderParam(headerParts, req);
					}
				}
			}
		}

		/**
		 * @param request-
		 *            a ReqObj containing method and path filled in by parseReq this
		 *            method will perform the requested method, though only GET works.
		 */
		public void doMethod(ReqObj request) {
			String method = request.getMethod(); // store method
			float ver = request.getVer();
			String notImpl = "Not Implemented";
			String badReq = "Bad Request";
			String wrongVer = "HTTP Version Not Supported";
			if (ver > 0 && ver <= 1.0) {
				getFileInfo(request);
				switch (method) {
				case "GET":
					doGet(request, false);
					break;
				case "POST":
					doGet(request, false);
					break;
				case "HEAD":
					doGet(request, true);
					break;
				case "DELETE":
					returnResponse(501, notImpl.getBytes(), notImpl.length(), request);
					break;
				case "PUT":
					returnResponse(501, notImpl.getBytes(), notImpl.length(), request);
					break;
				case "LINK":
					returnResponse(501, notImpl.getBytes(), notImpl.length(), request);
					break;
				case "UNLINK":
					returnResponse(501, notImpl.getBytes(), notImpl.length(), request);
					break;
				default:
					returnResponse(400, badReq.getBytes(), badReq.length(), request);
					break;
				}
			} else {
				returnResponse(505, wrongVer.getBytes(), wrongVer.length(), request);
			}
		}

		/**
		 * @param req-
		 *            the GET request This method will perform get a resource defined in
		 *            the ReqObj. Then it will call return response with the correct
		 *            status and content. If there is an error in fetching the resource
		 *            give null content, and the correct code.
		 **/
		private void doGet(ReqObj req, boolean head) {
			File file = req.getResource();
			byte[] contents = "".getBytes();
			// file must not be a directory and has to exist
			if (file.exists() && !file.isDirectory()) {
				if (req.ifModified) {
					long lastModified = req.getDate().getTime();
					long ifModified = req.ifModifiedDate.getTime();
					if (lastModified < ifModified) {
						req.setStatus(304);
					}
				}
				if (req.getStatus() != 304) {
					if (file.canRead()) { // file is readable
						try { // read and return contents of file
							Path path = Paths.get(file.toString());
							contents = Files.readAllBytes(path);
							req.setStatus(200);
						} catch (AccessDeniedException e) {
							String notReadable = "Forbidden";
							contents = notReadable.getBytes();
							req.setStatus(403);
						} catch (Exception e) {
							req.setStatus(500);
							contents = "Internal Server Error".getBytes();
							String error = getStackTrace(e);
							LOGGER.log(Level.SEVERE, error);
						}
					} else {
						String notReadable = "Forbidden";
						contents = notReadable.getBytes();
						req.setStatus(403);
					}
				}
			} else {
				String fourOFour = "File not found";
				contents = fourOFour.getBytes();
				req.setStatus(404);
			}
			if (!head && req.getStatus() != 304) {
				returnResponse(req.getStatus(), contents, contents.length, req);
			} else {
				returnResponse(req.getStatus(), null, contents.length, req);
			}

		}

		private void getFileInfo(ReqObj req) {
			if (req != null) {
				File file = req.getResource();
				// file must not be a directory and has to exist
				if (file.exists() && !file.isDirectory()) {
					req.setDate(new Date(file.lastModified()));
					req.setSize(file.length());
					if (file.canRead() && file.canWrite()) { // file is
																// readable/writable
						req.setPerm(2);
					} else if (file.canRead()) {
						req.setPerm(1);
					}
				} else {
					req.setPerm(0);
				}
			}
		}

		private String getServerTime(Date date) {
			SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
			dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
			return dateFormat.format(date);
		}

		private String getMIME(String ext) {
			switch (ext) {
			case "txt":
			case "c":
			case "cc":
			case "h":
				return "text/plain";
			case "css":
				return "text/css";
			case "html":
			case "htm":
				return "text/html";
			case "jpeg":
			case "jpe":
			case "jpg":
				return "image/jpeg";
			case "js":
				return "application/x-javascript";
			case "gif":
			case "png":
				return "image/" + ext;
			case "gz":
			case "gzip":
				return "application/x-gzip";
			case "zip":
			case "pdf":
				return "application/" + ext;
			default:
				return "application/octet-stream";
			}
		}

		private String doHeader(ReqObj obj, int status) {
			String date = getServerTime(new Date());
			StringBuilder header = new StringBuilder();
			String[] extArr = null;
			String ext = "";
			if (obj != null) {
				String filePath = obj.getResource().toString();
				extArr = filePath.split("\\.");
				ext = extArr[extArr.length - 1].toLowerCase();
			}
			header.append("Date: " + date);
			header.append("\r\n");
			header.append("Allow: GET, POST, HEAD");
			header.append("\r\n");
			header.append("Content-Encoding: identity");
			header.append("\r\n");
			if (obj != null && (status == 200 || status == 304)) {
				Date nowYear = new Date(System.currentTimeMillis() + 365 * 24 * 60 * 60 * 1000L);
				header.append("Expires: " + getServerTime(nowYear));
				header.append("\r\n");
				header.append("Last-Modified: " + getServerTime(obj.getDate()));
				header.append("\r\n");
				header.append("Content-Type: " + getMIME(ext));
				header.append("\r\n");
			} else {
				// Content length for 404?
				header.append("Content-Type: " + getMIME("txt"));
				header.append("\r\n");
			}
			return header.toString();

		}

		/**
		 * @param status-
		 *            the status code to be returned
		 * @param content-
		 *            a byte array containing the contents of what needs to get to
		 *            client Print request and other info pertianing to the Client
		 **/
		private void returnResponse(int status, byte[] content, long length, ReqObj request) {
			String log = logBuilder(status);
			LOGGER.log(Level.INFO, log);
			String procHeader = doHeader(request, status);
			try (PrintStream pstream = new PrintStream(clientSocket.getOutputStream())) {
				pstream.write((codeString(status) + "\r\n").getBytes());
				pstream.write(procHeader.getBytes());
				if (content != null) {
					pstream.write(("Content-Length: " + content.length + "\r\n\r\n").getBytes());
					pstream.write(content);
					pstream.write("\r\n".getBytes());
				} else if (length != 0) {
					pstream.write(("Content-Length: " + length + "\r\n\r\n").getBytes());
				}
				pstream.flush();
				Thread.sleep(250);
			} catch (Exception e) {
				String error = getStackTrace(e);
				LOGGER.log(Level.SEVERE, error);
			}
		}

		@Override
		/**
		 * perform the client request if appropriate and within the alloted time of 3s
		 */
		public void run() {
			Socket client = null;
			BufferedReader in = null;
			try {
				client = clientSocket;
				in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
				// Reads the request from the client
				List<String> header = new ArrayList<>();
				reqStr = in.readLine();
				// We got something so no more timeout

				while (true) {
					StringBuilder line = new StringBuilder();
					char c = 0;

					while (c != '\n' && c != Character.MAX_VALUE) {
						c = (char) in.read();
						line.append(Character.toString(c));
					}
					if (line.toString().equals("\r\n") || line.toString().equals("\n") || c == Character.MAX_VALUE) {
						break;
					} else {
						header.add(line.toString());
					}
				}

				// Parse the request, we can do a switch case based on request
				ReqObj req = parseReq(reqStr);
				if (req != null) {
					parseHeader(header, req);
					doMethod(req);
				} else {
					// just incase we get null object
					byte[] badReq = "Bad Request".getBytes();
					returnResponse(400, badReq, badReq.length, null);
				}
			} catch (SocketTimeoutException e) {
				byte[] reqTimeout = "Request Timeout".getBytes();
				returnResponse(408, reqTimeout, reqTimeout.length, null);
			} catch (Exception e) {
				byte[] serverError = "Internal Server Error".getBytes();
				returnResponse(500, serverError, serverError.length, null);
				String error = getStackTrace(e);
				LOGGER.log(Level.SEVERE, error);
			} finally {
				try {
					if (client != null)
						client.close();
					if (in != null)
						in.close();
				} catch (Exception e) {
					String error = getStackTrace(e);
					LOGGER.log(Level.SEVERE, error);
				}
			}
		}
	}

	/*
	 * A request object holding the method type (GET, POST, etc.) and resource to be
	 * read
	 */
	class ReqObj {
		private String httpMethod;
		private File resource;
		private float httpVer;
		private long fileSize = 0;
		// perm: 0=doesnt exist, 1=read/no write, 2=read/write
		private int perm = 0;
		private Date modified;
		private int status = 0;
		private boolean ifModified = false;
		private Date ifModifiedDate;

		ReqObj(String httpMethod, File resource, float httpVer) {
			this.httpMethod = httpMethod;
			this.resource = resource;
			this.httpVer = httpVer;
		}

		public boolean getIfModified() {
			return ifModified;
		}

		public void setIfModified(boolean ifModified) {
			this.ifModified = ifModified;
		}

		public Date getIfModifiedDate() {
			return this.ifModifiedDate;
		}

		public void setIfModifiedDate(Date ifModifiedDate) {
			this.ifModifiedDate = ifModifiedDate;
		}

		public int getStatus() {
			return this.status;
		}

		public void setStatus(int status) {
			this.status = status;
		}

		public Date getDate() {
			return this.modified;
		}

		public void setDate(Date modified) {
			this.modified = modified;
		}

		public long getSize() {
			return this.fileSize;
		}

		public void setSize(long size) {
			this.fileSize = size;
		}

		public void setPerm(int perm) {
			this.perm = perm;
		}

		public int getPerm() {
			return this.perm;
		}

		public float getVer() {
			return this.httpVer;
		}

		public void setVer(float httpVer) {
			this.httpVer = httpVer;
		}

		public String getMethod() {
			return this.httpMethod;
		}

		public void setMethod(String httpMethod) {
			this.httpMethod = httpMethod;
		}

		public File getResource() {
			return this.resource;
		}

		public void setResource(File resource) {
			this.resource = resource;
		}
	}

}
