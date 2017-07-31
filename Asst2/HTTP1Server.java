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
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HTTP1Server {

	private ServerSocket serverSocket; // the main socket for the server
	private int port; // which port it exists on gotten from command line
	private static final Logger LOGGER = Logger.getLogger(HTTP1Server.class.getName());

	/**
	 * Constructor to start the server and creates a new thread for each
	 * connection
	 */
	HTTP1Server(String[] args) {

		// Initialize a serverSocket to accept clients
		try {
			this.port = Integer.parseInt(args[0]);
			this.serverSocket = new ServerSocket(port);

		} catch (Exception e) {
			String error = getStackTrace(e);
			LOGGER.log(Level.SEVERE, error);
		}
	}

	/**
	 * @param throwable
	 *            Prints out the stack trace of the error that was thrown
	 */
	public static String getStackTrace(final Throwable throwable) {
		final StringWriter sw = new StringWriter();
		final PrintWriter pw = new PrintWriter(sw, true);
		throwable.printStackTrace(pw);
		return sw.getBuffer().toString();
	}

	/**
	 * Allow the server to accept the client where, in a state of rest, the
	 * server can accept at most 5 clients at once. Otherwise, a max of 50
	 * clients can be accepted.
	 */
	public void start() {
		if (serverSocket != null && !serverSocket.isClosed()) {
			String usage = "HTTP 1.0 server listening on port " + port;
			// Get the ThreadFactory implementation to use
			ThreadFactory threadFactory = Executors.defaultThreadFactory();
			// creating the ThreadPoolExecutor
			ThreadPoolExecutor executorPool = new ThreadPoolExecutor(5, 50, 0, TimeUnit.SECONDS,
					new SynchronousQueue<Runnable>(), threadFactory);
			Socket client = null;
			LOGGER.log(Level.INFO, usage);
			while (serverSocket.isBound()) {
				try { // Accept the client
					client = serverSocket.accept();
					client.setSoTimeout(3000); // set timeout to 3000
					executorPool.execute(new HTTPThread(client, port));
				} catch (RejectedExecutionException e) {
					String serviceUnav = "Service Unavailable";
					HTTPThread bad = new HTTPThread(client, port);
					bad.returnResponse(503, serviceUnav.getBytes(), serviceUnav.length(), null);
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
			LOGGER.log(Level.INFO, "Usage: java -cp . HTTP1Server 3456");
			return;
		}
		System.setProperty("java.util.logging.SimpleFormatter.format",
				"[%1$tm/%1$td/%1$tY %1$tH:%1$tM:%1$tS] %5$s%6$s%n");
		HTTP1Server server = new HTTP1Server(args);
		server.start();
		return;
	}

	/**
	 * Thread to handle each HTTP request
	 */
	static class HTTPThread implements Runnable {
		private Socket clientSocket;
		private int port;
		private String reqStr;
		private BufferedReader in = null;

		HTTPThread(Socket c, int port) {
			this.port = port;
			this.clientSocket = c;
		}

		/**
		 * Add appropriate response depdning on the status code
		 */
		private String codeString(int status) {
			switch (status) {
			case 200:
				return "HTTP/1.0 200 OK";

			case 204:
				return "HTTP/1.0 204 No Content";

			case 304:
				return "HTTP/1.0 304 Not Modified";

			case 400:
				return "HTTP/1.0 400 Bad Request";

			case 403:
				return "HTTP/1.0 403 Forbidden";

			case 404:
				return "HTTP/1.0 404 Not Found";

			case 405:
				return "HTTP/1.0 405 Method Not Allowed";

			case 408:
				return "HTTP/1.0 408 Request Timeout";

			case 411:
				return "HTTP/1.0 411 Length Required";

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
		 * @return a ReqObj containing the method and path to resource This
		 *         method will parse a request string and return a request
		 *         object
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
						String[] verNum = verArr[1].split("\\.");
						if (verNum[0].length() != 1 || verNum[1].length() != 1)
							return null;
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
					return new ReqObj(method, fullPath, ver, relativePath);
				} catch (Exception e) {
					String error = getStackTrace(e);
					LOGGER.log(Level.SEVERE, error);
					return null;
				}

			} else { // bad path request
				return null;
			}

		}

		/**
		 * @param input-
		 *            The input string to be parsed
		 * @return a date with correct format upon parsing
		 */
		private Date parseDate(String input) {
			SimpleDateFormat parser = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
			parser.setTimeZone(TimeZone.getTimeZone("GMT"));
			try {
				return parser.parse(input);

			} catch (ParseException e) {
				String error = getStackTrace(e);
				LOGGER.log(Level.SEVERE, error);
				return null;
			}
		}

		/**
		 * @param headerParts-
		 *            An array containing the parts of a header
		 * @param req-
		 *            A request object input Set the if-modified-since parameter
		 *            of header
		 */
		private void parseHeaderParam(String[] headerParts, ReqObj req) {
			String headerData = headerParts[1];
			if (headerData.charAt(0) == ' ') {
				headerData = headerData.substring(1);
			}
			if (headerParts[0].equalsIgnoreCase("if-modified-since")) {
				Date ifModified = parseDate(headerData);
				if (ifModified != null) {
					req.ifModified = true;
					req.ifModifiedDate = ifModified;
				}
			}
			if (headerParts[0].equalsIgnoreCase("content-length")) {
				long lengthHeader = -1;
				try {
					lengthHeader = Long.parseLong(headerData);
				} catch (Exception e) {
					String error = getStackTrace(e);
					LOGGER.log(Level.SEVERE, error);
				}
				if (lengthHeader >= 0) {
					req.lengthHeader = true;
					req.lengthHeaderData = lengthHeader;
				}
			}
			if (headerParts[0].equalsIgnoreCase("Content-Type")
					&& headerData.equalsIgnoreCase("application/x-www-form-urlencoded")) {
				req.typeHeader = true;
			}
			if (headerParts[0].equalsIgnoreCase("From")) {
				req.fromField = headerData;
			}
			if (headerParts[0].equalsIgnoreCase("User-Agent")) {
				req.userAgnet = headerData;
			}
		}

		/**
		 * @param header-
		 *            A list of contents of the header
		 * @param req-
		 *            A input request object This method parses the header
		 *            contents into a request object
		 */
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
		 *            a ReqObj containing method and path filled in by parseReq
		 *            this method will perform the requested method, though only
		 *            GET works.
		 */
		public void doMethod(ReqObj request) {
			String method = request.httpMethod; // store method
			float ver = request.httpVer;
			String notImpl = "Not Implemented";
			String badReq = "Bad Request";
			String wrongVer = "HTTP Version Not Supported";
			// NEED TO CHANGE THIS BACK to 1.0
			if (ver > 0 && ver <= 1.0) {
				getFileInfo(request);
				switch (method) {
				case "GET":
					doGet(request, false);
					break;
				case "POST":
					doPost(request);
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
		 * @param length
		 * Decode the paylaod as per RFC-3986
		 */
		private String getPayload(long length) {
			StringBuilder line = new StringBuilder();
			try {
				for (int i = 0; i < length; i++) {
					char c = (char) in.read();
					line.append(Character.toString(c));
				}
				return line.toString();
			} catch (Exception e) {
				String error = getStackTrace(e);
				LOGGER.log(Level.SEVERE, error);
				return null;
			}
		}

		/**
		 * @param Process p
		 * @param payload
		 * This method will the send the payload to tge CGI script via standard in
		 */
		private boolean sendInput(Process p, String payload) {
			try (OutputStream stdin = p.getOutputStream();
					 BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(stdin));) {
				writer.write(payload);
				writer.flush();
				return true;
			} catch (Exception e) {
				String error = getStackTrace(e);
				LOGGER.log(Level.SEVERE, error);
				return false;
			}
		}

		/**
		 * @param Process p
		 * Recieve the output of the execution of a CGI file
		 */
		private String getOutput(Process p) {
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));) {
				StringBuilder builder = new StringBuilder();
				String line = null;
				// may need to account for when nothing is printed back
				while ((line = reader.readLine()) != null) {
					if (builder.length() == 0) {
						builder.append(line);
					} else {
						builder.append(System.getProperty("line.separator")); //OS-dependent line separator
						builder.append(line);
					}

				}
				return builder.toString();
			} catch (Exception e) {
				String error = getStackTrace(e);
				LOGGER.log(Level.SEVERE, error);
				return null;
			}
		}

		/**
		 * @param req- THe request Object
		 * This method will perform a POST request as defined in
		 * the request object. It will correctly parse the resource
		 * and properly execute the CGI file. Appropriate responses
		 * are returned depending on the error type (if any).
		 */
		private void doPost(ReqObj req) {
			if (!req.lengthHeader) {
				String noLength = "Length Required";
				returnResponse(411, noLength.getBytes(), noLength.length(), req);
				return;
			} else if (!req.typeHeader) {
				String noType = "Internal Server Error";
				returnResponse(500, noType.getBytes(), noType.length(), req);
				return;
			}
			File file = req.resource;
			if (file.exists() && !file.isDirectory()) {
				String filePath = req.resource.toString();
				String[] extArr = filePath.split("\\.");
				String ext = extArr[extArr.length - 1].toLowerCase();
				if (!ext.equalsIgnoreCase("cgi")) {
					returnResponse(405, "Method Not Allowed".getBytes(), "Method Not Allowed".length(), req);
					return;
				}
				if (file.canExecute()) { //execute cgi file
					String payload = getPayload(req.lengthHeaderData);
					if (payload != null) {
						byte[] stdout = null;
						long length = 0;
						try {
							payload = java.net.URLDecoder.decode(payload, "UTF-8");
							String hostIP = Inet4Address.getLocalHost().getHostAddress();
							ProcessBuilder pb = new ProcessBuilder(filePath, payload);
							pb.redirectErrorStream(true);
							Map<String, String> env = pb.environment();
							env.put("CONTENT_LENGTH", String.valueOf(payload.length()));
							env.put("SCRIPT_NAME", req.relativePath);
							env.put("SERVER_NAME", hostIP);
							env.put("SERVER_PORT", String.valueOf(port));
							if (req.fromField != null)
								env.put("HTTP_FROM", req.fromField);
							if (req.userAgnet != null)
								env.put("HTTP_USER_AGENT", req.userAgnet);
							Process p = pb.start();
							sendInput(p, payload);
							String output = getOutput(p);
							if (output != null && output.length() == 0) {
								req.status = 204;
							} else if (output != null) {
								req.status = 200;
								stdout = output.getBytes();
								length = output.length();
							}
						} catch (Exception e) {
							String error = getStackTrace(e);
							LOGGER.log(Level.SEVERE, error);
							returnResponse(500, "Internal Server Error".getBytes(), "Internal Server Error".length(),
									req);
							return;
						}
						returnResponse(req.status, stdout, length, req);
					} else {
						returnResponse(500, "Internal Server Error".getBytes(), "Internal Server Error".length(), req);
					}
				} else {
					returnResponse(403, "Forbidden".getBytes(), "Forbidden".length(), req);
				}
			} else {
				returnResponse(404, "File not found".getBytes(), "File not found".length(), req);
			}
		}

		/**
		 * @param req-
		 *            the GET request This method will perform get a resource
		 *            defined in the ReqObj. Then it will call return response
		 *            with the correct status and content. If there is an error
		 *            in fetching the resource give null content, and the
		 *            correct code.
		 **/
		private void doGet(ReqObj req, boolean head) {
			File file = req.resource;
			byte[] contents = "".getBytes();
			// file must not be a directory and has to exist
			if (file.exists() && !file.isDirectory()) {
				if (req.ifModified && !head) {
					long lastModified = req.date.getTime();
					long ifModified = req.ifModifiedDate.getTime();
					if (lastModified < ifModified) {
						req.status = 304;
					}
				}
				if (req.status != 304) {
					if (file.canRead()) { // file is readable
						try { // read and return contents of file
							Path path = Paths.get(file.toString());
							contents = Files.readAllBytes(path);
							req.status = 200;
						} catch (AccessDeniedException e) {
							String notReadable = "Forbidden";
							contents = notReadable.getBytes();
							req.status = 403;
						} catch (Exception e) {
							req.status = 500;
							contents = "Internal Server Error".getBytes();
							String error = getStackTrace(e);
							LOGGER.log(Level.SEVERE, error);
						}
					} else {
						String notReadable = "Forbidden";
						contents = notReadable.getBytes();
						req.status = 403;
					}
				}
			} else {
				String fourOFour = "File not found";
				contents = fourOFour.getBytes();
				req.status = 404;
			}
			if (!head && req.status != 304) {
				returnResponse(req.status, contents, contents.length, req);
			} else {
				returnResponse(req.status, null, contents.length, req);
			}

		}

		/**
		 * @param req-
		 *            The request object This method fills the request object
		 *            with necessary read/write permissions as well checking
		 *            file location.
		 */
		private void getFileInfo(ReqObj req) {
			if (req != null) {
				File file = req.resource;
				// file must not be a directory and has to exist
				if (file.exists() && !file.isDirectory()) {
					req.date = (new Date(file.lastModified()));
				}
			}
		}

		/**
		 * @param date-
		 *            the input date
		 * @return the time of the server as a String
		 */
		private String getServerTime(Date date) {
			SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
			dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
			return dateFormat.format(date);
		}

		/**
		 * @param ext-
		 *            the file extension
		 * @return the type of MIME depending on the file extension
		 */
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

		/**
		 * @param obj-
		 *            A request object
		 * @param status-
		 *            status code
		 * @return header content with necessary appended information
		 */
		private String doHeader(ReqObj obj, int status) {
			String date = getServerTime(new Date());
			StringBuilder header = new StringBuilder();
			String[] extArr = null;
			String ext = "";
			if (obj != null) {
				String filePath = obj.resource.toString();
				extArr = filePath.split("\\.");
				ext = extArr[extArr.length - 1].toLowerCase();
			}
			header.append("Date: " + date);
			header.append("\r\n");
			header.append("Allow: GET, POST, HEAD");
			header.append("\r\n");
			header.append("Content-Encoding: identity");
			header.append("\r\n");
			if (obj != null && (status == 200 || status == 304 || status == 204)) {
				Date nowYear = new Date(System.currentTimeMillis() + 365 * 24 * 60 * 60 * 1000L);
				if (!obj.httpMethod.equalsIgnoreCase("post")) {
					header.append("Expires: " + getServerTime(nowYear));
					header.append("\r\n");
					header.append("Last-Modified: " + getServerTime(obj.date));
					header.append("\r\n");
					header.append("Content-Type: " + getMIME(ext));
					header.append("\r\n");
				} else if (status != 204 || obj.httpMethod.equalsIgnoreCase("post")) {
					header.append("Expires: " + getServerTime(nowYear));
					header.append("\r\n");
					header.append("Content-Type: text/html");
					header.append("\r\n");
				}
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
		 *            a byte array containing the contents of what needs to get
		 *            to client Print request and other info pertianing to the
		 *            Client
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
				} else {
					pstream.write("\r\n".getBytes());
				}
				pstream.flush();
				if (status != 503)
					Thread.sleep(250);
				clientSocket.close();
			} catch (Exception e) {
				String error = getStackTrace(e);
				LOGGER.log(Level.SEVERE, error);
			}
		}

		@Override
		/**
		 * perform the client request if appropriate and within the alloted time
		 * of 3s
		 */
		public void run() {
			Socket client = null;
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
						header.add(line.toString().substring(0, line.toString().length() - 2));
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
				return;
			} catch (Exception e) {
				byte[] serverError = "Internal Server Error".getBytes();
				returnResponse(500, serverError, serverError.length, null);
				String error = getStackTrace(e);
				LOGGER.log(Level.SEVERE, error);
				return;
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
	 * A request object holding the method type (GET, POST, etc.) and resource
	 * to be read
	 */
	static class ReqObj {
		private String httpMethod;
		private File resource;
		private String relativePath;
		private float httpVer;
		// perm: 0=doesnt exist, 1=read/no write, 2=read/write
		private int status = 0;
		private boolean ifModified = false;
		private Date ifModifiedDate;
		private Date date;
		private boolean lengthHeader = false;
		private long lengthHeaderData = 0;
		private boolean typeHeader = false;
		private String fromField = null;
		private String userAgnet = null;

		ReqObj(String httpMethod, File resource, float httpVer, String relativePath) {
			this.relativePath = relativePath;
			this.httpMethod = httpMethod;
			this.resource = resource;
			this.httpVer = httpVer;
		}

	}

}
