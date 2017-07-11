import java.net.*;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.util.Locale;
import java.util.TimeZone;

public class PartialHTTP1Server {

	private ServerSocket serverSocket; // the main socket for the server
	private int port; // which port it exists on gotten from command line

	/**
	 * Constructor to start the server and creates a new thread for each
	 * connection
	 */
	PartialHTTP1Server(String[] args) {

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
			System.out.println("HTTP 1.0 server listening on port " + port);
			while (true) {
				try { // Accept the client
					Socket client = serverSocket.accept();
					client.setSoTimeout(30000); // set timeout to 3000
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
			System.out.println("Usage: java -cp . PartialHTTP1Server 3456");
			return;
		}
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
		private String[] header;

		HTTPThread(Socket c) {
			this.clientSocket = c;
		}

		/**
		 * Add appropriate response depdning on the status code
		 */
		private String codeString(int status) {
			switch (status) {
			case 200:
				return "HTTP/1.0 200 OK\r";

			case 304:
				return "HTTP/1.0 304 Not Modified\r";

			case 400:
				return "HTTP/1.0 400 Bad Request\r";

			case 403:
				return "HTTP/1.0 403 Forbidden\r";

			case 404:
				return "HTTP/1.0 404 Not Found\r";

			case 408:
				return "HTTP/1.0 408 Request Timeout\r";

			case 500:
				return "HTTP/1.0 500 Internal Server Error\r";

			case 501:
				return "HTTP/1.0 501 Not Implemented\r";

			case 503:
				return "HTTP/1.0 503 Service Unavailable\r";

			case 505:
				return "HTTP/1.0 505 HTTP Version Not Supported\r";

			default:
				return "HTTP/1.0 200 OK\r";
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
			float ver = 0;
			// Make sure that method request is formatted correctly
			if (reqArr.length == 3 && reqArr[0].length() > 0 && reqArr[0].chars().allMatch(Character::isLetter)
				&& reqArr[0].toUpperCase().equals(reqArr[0])) { 
				// set method
				method = reqArr[0];
			} else {
				return null;

			}

			if(reqArr[2].length() > 0){
				String[] verArr = reqArr[2].split("/");
				if(verArr.length != 2){
					return null;
				}
				if(verArr[0].equals("HTTP") && verArr[1].split("\\.").length == 2){
					try{
						ver = Float.parseFloat(verArr[1]);
					} catch(Exception e){
						return null;
					}
				} else{
					return null;
				}
			}
			// Set paths to be merged
			if (reqArr[1].length() > 0 && reqArr[1].charAt(0) == '/') {
				try {
					String relativePath = java.net.URLDecoder.decode(reqArr[1], "UTF-8");
					String workingDir = System.getProperty("user.dir");
					File dir = new File(workingDir);
					String fullPath = new File(dir,relativePath).getPath();
					return new ReqObj(method, fullPath, ver);
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
			float ver = request.getVer();
			String notImpl = "Not Implemented";
			String badrReq = "Bad Request";
			String wrongVer = "HTTP Version Not Supported";
			String procHeader = doHeader(request);
			request.setHeader(procHeader);
			if(ver > 0 && ver <= 1.0){
				switch (method) {
				case "GET":
					doGet(request);
					break;
				case "POST":
					doGet(request);
					break;
				case "HEAD":
					doGet(request);
					break;
				case "DELETE":
					returnResponse(501, procHeader, notImpl.getBytes());
					break;
				case "PUT":
					returnResponse(501, procHeader, notImpl.getBytes());
					break;
				case "LINK":
					returnResponse(501, procHeader, notImpl.getBytes());
					break;
				case "UNLINK":
					returnResponse(501, procHeader, notImpl.getBytes());
					break;
				default:
					returnResponse(400, procHeader, notImpl.getBytes());
					break;
				}
			}else{
			returnResponse(505, procHeader, wrongVer.getBytes());
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
			String procHeader = req.getHeader();
			// file must not be a directory and has to exist
			if (file.exists() && !file.isDirectory()) {
				if (file.canRead()) { // file is readable
					try { // read and return contents of file
						Path path = Paths.get(filePath);
						byte[] contents = Files.readAllBytes(path);
						returnResponse(200, procHeader, contents);
					} catch (Exception e) {
						returnResponse(500, procHeader, "Internal Server Error".getBytes());
						e.printStackTrace();
					}
				} else {
					String notReadable = "Forbidden";
					returnResponse(403, procHeader, notReadable.getBytes());
				}
			} else {
				String fourOFour = "File not found";
				returnResponse(404, procHeader, fourOFour.getBytes());
			}

		}

		private void getFileInfo(ReqObj req){
			String filePath = req.getResource();
			File file = new File(filePath);
			// file must not be a directory and has to exist
			if (file.exists() && !file.isDirectory()) {
				Path path = Paths.get(filePath);
				req.setDate(new Date(file.lastModified()));
				req.setSize(file.length());
				if (file.canRead() && file.canWrite()) { // file is readable/writable
					req.setPerm(2);
				} else if(file.canRead()){
					req.setPerm(1);
				}
			} else {
				req.setPerm(0);
			}
		}

		private String getServerTime(Date date) {
			SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
			dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
		    return dateFormat.format(date);
		}

		private String getMIME(String ext){
			switch(ext){
				case "txt":
					return "text/plain";
				case "html":
					return "text/" + ext;
				case "gif":
				case "jpeg":
				case "png":
					return "image/" + ext;
				case "gz":
				case "gzip":
					return "application/x-gzip";
				case "zip":
					return "application/zip";
				default:
					return "application/octet-stream";
			}
		}

		private String doHeader(ReqObj obj){
			String date = getServerTime(new Date());
			StringBuilder header = new StringBuilder();
			String[] extArr = {""};
			String ext = "";
			if(obj != null){
				extArr = obj.getResource().split("\\.");
				ext = extArr[extArr.length-1].toLowerCase();
			}
			header.append("Date: " + date);
			header.append("\n");
			header.append("Server: PartialHTTP1Server/1.0");
			header.append("\n");
			header.append("Allow: GET, HEAD, POST");
			header.append("\n");
			getFileInfo(obj);
			if(obj.getPerm() > 0){
				header.append("Expires: " + getServerTime(new Date()));
				header.append("\n");
				header.append("Last-Modified: " + getServerTime(obj.getDate()));
				header.append("\n");
				header.append("Content-Type: " + getMIME(ext));
				header.append("\n");
			} else{
				// Content length for 404?
				header.append("Content-Type: " + getMIME("html"));
				header.append("\n");
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
		private void returnResponse(int status, String procHeader, byte[] content) {
			System.out.println(logBuilder(status));

			try (PrintStream pstream = new PrintStream(clientSocket.getOutputStream())) {
				pstream.println(codeString(status));
				pstream.write(procHeader.getBytes());
				pstream.write(("Content-Length: " + content.length + "\n\n").getBytes());
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
					// We got something so no more timeout
					client.setSoTimeout(0);
				} catch (SocketTimeoutException e) {

					returnResponse(408, doHeader(null), "Request Timeout".getBytes());
					return;
				}

				// Parse the request, we can do a switch case based on request
				ReqObj req = parseReq(reqStr);
				if (req != null) {
					doMethod(req);
				} else {
					// just incase we get null object
					returnResponse(400, doHeader(null), "Bad Request".getBytes());
				}
			} catch (Exception e) {
				returnResponse(500, doHeader(null), "Internal Server Error".getBytes());
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
		private float httpVer;
		private long fileSize = 0;
		// perm: 0=doesnt exist, 1=read/no write, 2=read/write
		private int perm = 0;
		private Date modified;
		private String procHeader;

		ReqObj(String httpMethod, String resource, float httpVer) {
			this.httpMethod = httpMethod;
			this.resource = resource;
			this.httpVer = httpVer;
		}

		public String getHeader(){
			return this.procHeader;
		}

		public void setHeader(String procHeader){
			this.procHeader = procHeader;
		}

		public Date getDate(){
			return this.modified;
		}

		public void setDate(Date modified){
			this.modified = modified;
		}

		public long getSize(){
			return this.fileSize;
		}

		public void setSize(long size){
			this.fileSize = size;
		}

		public void setPerm(int perm){
			this.perm = perm;
		}

		public int getPerm(){
			return this.perm;
		}

		public float getVer(){
			return this.httpVer;
		}

		public void setVer(float httpVer){
			this.httpVer = httpVer;
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
