import java.lang.*;
import java.net.*;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.Path;

public class SimpleHTTPServer {

	ServerSocket serverSocket; //the main socket for the server
	int port; //which port it exists on gotten from command line

	/**
	 * Constructor to start the server and creates a new thread for each connection
	 */
	SimpleHTTPServer(String[] args) {

		// Initialize a serverSocket to accept clients
		try {
			this.port = Integer.parseInt(args[0]);
			this.serverSocket = new ServerSocket(port);

		} catch (Exception e) {
			e.printStackTrace();
			return;
		}
		System.out.println("HTTP server listening on port " + port);
		Socket client = null;
		while (true) {
			try { // Accept the client
				client = serverSocket.accept();
				client.setSoTimeout(3000); //set timeout to 3000
				HTTPThread clientThread = new HTTPThread(client);
				Thread t = new Thread(clientThread);
				t.start();
			} catch (Exception e) { // When we catch the error, print it out
				e.printStackTrace();
			}
		}
	}

	/*
	 * Instantiates the server class by passing in input array
	 */
	public static void main(String[] args) {
		// Check that our arguments are correct. If not we print message and exit.
		if (args.length != 1) {
			System.out.println("Usage: java -cp . SimpleHTTPServer 3456");
			return;
		}
		SimpleHTTPServer server = new SimpleHTTPServer(args);
		return;
	}

	/**
	 * Thread to handle each HTTP request
	 */
	class HTTPThread implements Runnable {
		Socket clientSocket;
		String reqStr;

		HTTPThread(Socket c) {
			this.clientSocket = c;
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
			int port = clientSocket.getPort();
			pre.append(addr + ":" + port + " - ");
			pre.append(reqStr + " - ");
			pre.append(status);
			return pre.toString();
		}

		/**
		* @param req- the request inputted by the user to be parsed
		* @return a ReqObj containing the method and path to resource
		* This method will parse a request string and return a request object
		**/
		private ReqObj parseReq(String req) {
			String method = "";
			String workingDir = ""; //path of root directory
			String relativePath = ""; //user supplied path
			String fullPath = ""; //merge root dir with relative path
			ReqObj request = null; //to be returned

			String[] reqArr = req.split(" ");
			if (reqArr.length < 2 || reqArr[0].length() < 3) { //make sure that method request is formated correctly
				return null;
			} else { //set method
				method = reqArr[0];
			}
			if (reqArr[1].length() > 0 && reqArr[1].charAt(0) == '/') { //set paths to be merged
				try {
					relativePath = java.net.URLDecoder.decode(reqArr[1], "UTF-8");
					workingDir = System.getProperty("user.dir");
				} catch (Exception e) {
					e.printStackTrace();
				}
				fullPath = workingDir + relativePath; //merge paths
			}  else { //bad path request
				return null;
			}
			return new ReqObj(method, fullPath);
		}

		/**
		 * @param request- a ReqObj containing method and path filled in by parseReq
		 * this method will perform the requested method, though only GET works.
		 */
		public void doMethod(ReqObj request) {
			String method = request.getMethod(); //store method
			String fullPath = request.getResource(); //store the resource path
			String notImpl = "Not Implemented";
			switch (method) {
			case "GET":
				request.setMethod("GET");
				doGet(request);
				break;
			case "HEAD":
				request.setMethod("HEAD");
				returnResponse(501, notImpl.getBytes());
				break;
			case "POST":
				request.setMethod("POST");
				returnResponse(501, notImpl.getBytes());
				break;
			case "PUT":
				request.setMethod("PUT");
				returnResponse(501, notImpl.getBytes());
				break;
			case "DELETE":
				request.setMethod("DELETE");
				returnResponse(501, notImpl.getBytes());
				break;
			default:
				String bad = "Bad Request";
				returnResponse(400, bad.getBytes());
				break;
			}
		}

		/**
		* @param req- the GET request
		* This method will perform get a resource defined in the ReqObj. Then it will call return response with the correct status and content.
		* If there is an error in fetching the resource give null content, and the correct code.
		**/
		private void doGet(ReqObj req) {
			String filePath = req.getResource();
			File file = new File(filePath);
			if (file.exists() && !file.isDirectory()) { //file must not be a directory and has to exist
				if (file.canRead()) { //file is readable
					try { //read and return contents of file
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
		* @param status- the status code to be returned
		* @param content- a byte array containing the contents of what needs to get to client
		* Print request and other info pertianing to the Client
		**/
		private void returnResponse(int status, byte[] content) {
			System.out.println(logBuilder(status));
			try(PrintStream pstream = new PrintStream(clientSocket.getOutputStream())) {
				HTTPCodes codes = new HTTPCodes();
				pstream.println(codes.toString(status));
				pstream.write(content);
				pstream.flush();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}


		@Override
		/**
		 * perform the client request if appropriate and within the alloted time of 3s
		 */
		public void run() {
			try(
				    Socket client = clientSocket;
				    BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
				) {
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
				Thread.sleep(250);
			} catch (Exception e) {
				returnResponse(500, "Internal Server Error".getBytes());
				e.printStackTrace();
			}
		}
	}

	/*
	 * A request object holding the method type (GET, POST, etc.) and resource to be read
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

	/**
	 * Class containing the HTTP status codes to be set when necessary
	 */
	public class HTTPCodes {

		public HTTPCodes() {
		}

		/**
		 * Add appropriate response depdning on the status code
		 */
		public String toString(int status) {
			switch (status) {
			case 200:
				return "HTTP/0.8 200 OK\r\n";

			case 400:
				return "HTTP/0.8 400 Bad Request\r\n";

			case 501:
				return "HTTP/0.8 501 Not Implemented\r\n";

			case 500:
				return "HTTP/0.8 500 Internal Server Error\r\n";

			case 404:
				return "HTTP/0.8 404 Not Found\r\n";

			case 408:
				return "HTTP/0.8 408 Request Timeout\r\n";

			default:
				return "HTTP/0.8 200 OK\r\n";
			}
		}
	}
}
