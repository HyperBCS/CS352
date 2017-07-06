import java.net.*;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.Path;

public class SimpleHTTPServer {

	private ServerSocket serverSocket; // the main socket for the server
	private int port; // which port it exists on gotten from command line

	/**
	 * Constructor to start the server and creates a new thread for each
	 * connection
	 */
	SimpleHTTPServer(String[] args) {

		// Initialize a serverSocket to accept clients
		try {
			this.port = Integer.parseInt(args[0]);
			this.serverSocket = new ServerSocket(port);

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void start() {
		if (serverSocket != null && !serverSocket.isClosed()) {
			System.out.println("HTTP server listening on port " + port);
			while (true) {
				try { // Accept the client
					Socket client = serverSocket.accept();
					client.setSoTimeout(3000); // set timeout to 3000
					HTTPThread clientThread = new HTTPThread(client);
					Thread t = new Thread(clientThread);
					t.start();
				} catch (Exception e) { // When we catch the error, print it out
					e.printStackTrace();
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
			System.out.println("Usage: java -cp . SimpleHTTPServer 3456");
			return;
		}
		SimpleHTTPServer server = new SimpleHTTPServer(args);
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
				return "200 OK\r\n";

			case 400:
				return "400 Bad Request\r\n";

			case 501:
				return "501 Not Implemented\r\n";

			case 500:
				return "500 Internal Server Error\r\n";

			case 404:
				return "404 Not Found\r\n";

			case 408:
				return "408 Request Timeout\r\n";

			default:
				return "200 OK\r\n";
			}
		}

		/**
		 * Log exactly when the request was made with date and time
		 */
		private String logBuilder(int status) {
			StringBuilder pre = new StringBuilder();
			SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
			Date date = new Date();
			pre.append("[" + dateFormat.format(date) + "] ");
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
			if(req == null){
				return null;
			}
			String[] reqArr = req.split(" ");
			String method;
			// Make sure that method request is formatted correctly
			if (reqArr.length == 2 && reqArr[0].length() > 0 && reqArr[0].toUpperCase().equals(reqArr[0])) { 
				// set method
				method = reqArr[0];
			} else {
				return null;

			}
			// Set paths to be merged
			if (reqArr[1].length() > 0 && reqArr[1].charAt(0) == '/') {
				try {
					String relativePath = java.net.URLDecoder.decode(reqArr[1], "UTF-8");
					String workingDir = System.getProperty("user.dir");
					File dir = new File(workingDir);
					String fullPath = new File(dir,relativePath).getPath();
					return new ReqObj(method, fullPath);
				} catch (Exception e) {
					e.printStackTrace();
					return null;
				}

			} else { // bad path request
				return null;
			}

		}

		/**
		 * @param request-
		 *            a ReqObj containing method and path filled in by parseReq
		 *            this method will perform the requested method, though only
		 *            GET works.
		 */
		public void doMethod(ReqObj request) {
			String method = request.getMethod(); // store method
			String notImpl = "Not Implemented";
			switch (method) {
			case "GET":
				doGet(request);
				break;
			default:
				returnResponse(501, notImpl.getBytes());
				break;
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
		private void doGet(ReqObj req) {
			String filePath = req.getResource();
			File file = new File(filePath);
			// file must not be a directory and has to exist
			if (file.exists() && !file.isDirectory()) {
				if (file.canRead()) { // file is readable
					try { // read and return contents of file
						Path path = Paths.get(filePath);
						byte[] contents = Files.readAllBytes(path);
						returnResponse(200, contents);
					} catch (Exception e) {
						returnResponse(500, "Internal Server Error".getBytes());
						e.printStackTrace();
					}
				} else {
					String notReadable = "Internal Server Error";
					returnResponse(500, notReadable.getBytes());
				}
			} else {
				String fourOFour = "File not found";
				returnResponse(404, fourOFour.getBytes());
			}

		}

		/**
		 * @param status-
		 *            the status code to be returned
		 * @param content-
		 *            a byte array containing the contents of what needs to get
		 *            to client Print request and other info pertianing to the
		 *            Client
		 **/
		private void returnResponse(int status, byte[] content) {
			System.out.println(logBuilder(status));
			try (PrintStream pstream = new PrintStream(clientSocket.getOutputStream())) {
				pstream.println(codeString(status));
				pstream.write(content);
				pstream.flush();
				Thread.sleep(250);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		@Override
		/**
		 * perform the client request if appropriate and within the alloted time
		 * of 3s
		 */
		public void run() {
			try (Socket client = clientSocket;
					BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));) {
				// Reads the request from the client
				try {
					reqStr = in.readLine();
				} catch (SocketTimeoutException e) {
					returnResponse(408, "Request Timeout".getBytes());
					return;
				}
				// Parse the request, we can do a switch case based on request
				ReqObj req = parseReq(reqStr);
				if (req != null) {
					doMethod(req);
				} else {
					// just incase we get null object
					returnResponse(400, "Bad Request".getBytes());
				}
			} catch (Exception e) {
				returnResponse(500, "Internal Server Error".getBytes());
				e.printStackTrace();
			}
		}
	}

	/*
	 * A request object holding the method type (GET, POST, etc.) and resource
	 * to be read
	 */
	class ReqObj {
		private String httpMethod;
		private String resource;

		ReqObj(String httpMethod, String resource) {
			this.httpMethod = httpMethod;
			this.resource = resource;
		}

		public String getMethod() {
			return this.httpMethod;
		}

		public void setMethod(String httpMethod) {
			this.httpMethod = httpMethod;
		}

		public String getResource() {
			return this.resource;
		}

		public void setResource(String resource) {
			this.resource = resource;
		}
	}

}
