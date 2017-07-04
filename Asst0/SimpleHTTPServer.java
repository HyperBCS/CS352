import java.lang.*;
import java.net.*;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.io.*;



public class SimpleHTTPServer{

	ServerSocket serverSocket;
	int port;

	SimpleHTTPServer(String[] args){

		// Initialize a serverSocket to accept clients
		try{
			this.port = Integer.parseInt(args[0]);
			this.serverSocket = new ServerSocket(port);

		} catch(Exception e){
			System.out.println(e);
		}
		
		Socket client = null;
		while(true){
			try{			
				// Accept the client
				client = serverSocket.accept();
				HTTPThread clientThread = new HTTPThread(client);
				Thread t = new Thread(clientThread);
				t.start();
			} catch(Exception e){
				// When we catch the error, print it out
				System.out.println(e);
			}
		}
	}

	public static void main(String[] args){
		// Check that our arguments are correct. If not we print message and exit.
		if(args.length != 1){
			System.out.println("Usage: java -cp . SimpleHTTPServer 3456");
			return;
		}
		SimpleHTTPServer server = new SimpleHTTPServer(args);
		return;
	}

	class HTTPThread implements Runnable{
		Socket clientSocket;

		HTTPThread(Socket c){
			this.clientSocket = c;
		}

		private String logBuilder(Socket clientSocket, String req){
			StringBuilder pre = new StringBuilder();
			SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
			Date date = new Date();
			pre.append("[" + dateFormat.format(date) + "] ");
			String addr = clientSocket.getInetAddress().getHostName();
			int port = clientSocket.getPort();
			pre.append(addr + ":" + port + " - ");
			pre.append(req);
			return pre.toString();
		}

		/**
		*This method will parse a request string and return a request object
		**/
		private ReqObj parseReq(String req){
			return null;
		}

		/**
		*This method will perform get a resource defined in the ReqObj. Then it will call return response with the correct status and content.
		* If there is an error in fetching the resource give null content, and the correct code.
		**/
		private void doGet(ReqObj req){}

		/**
		*This method will take a status, and content to be returned to the user.
		**/
		private void returnResponse(int status, byte[] content){
			try(PrintStream pstream = new PrintStream(clientSocket.getOutputStream())){
			HttpCodes codes = new HttpCodes();
			pstream.println(codes.toString(status));
			pstream.write(content);
			} catch(Exception e){
				System.out.println(e);
			}

		}

		@Override
		public void run(){
			try(
				Socket client = clientSocket;
		    	BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
				){
			// Reads the request from the client
           	String s = in.readLine();
           	// Prints the request along with other info about client
			System.out.println(logBuilder(clientSocket, s));
			// Parse the request, we can do a switch case based on request
			ReqObj req = parseReq(s);

			// THis is just to test sending a status and content to responder.
			String respStr = "hello ram";
			returnResponse(200,respStr.getBytes());
			Thread.sleep(250);
			} catch(Exception e){
				System.out.println(e);
			}
		}
	}

	class ReqObj{
		String httpMethod;
		String resource;
		ReqObj(String httpMethod, String resource){
			this.httpMethod = httpMethod;
			this.resource = resource;
		}

		public String getMethod(){
			return this.httpMethod;
		}

		public void setMethod(String httpMethod){
			this.httpMethod = httpMethod;
		}

		public String getResource(){
			return this.resource;
		}

		public void setResource(String resource){
			this.resource = resource;
		}
	}

	public class HttpCodes {

	    public HttpCodes() {
	    }

	    public String toString(int status) {
	        switch (status) {
	            case 200:
	                return "HTTP/0.8 200 OK\r\n";
	                    
	            case 400:
	                return "HTTP/0.8 200 Bad Request\r\n";

	            case 501:
	                return "HTTP/0.8 501 Not Implemented\r\n";

	            case 500:
	                return "HTTP/0.8 500 Internal Server Error\r\n";
	                     
	            case 404:
	                return "HTTP/0.8 404 Not Found\r\n";

	            case 408:
	                return "HTTP/0.8 408 Request Timeout\r\n";

	            default:
	                return "HTTP/0.8 200 Request Timeout\r\n";
	        }
	    }
	}
}