#include <stdio.h>
#include <stdlib.h>
#define MAXLEN 80

/*
A simple cgi script that echoes content-length and the request data 
based on code from https://www.cs.tut.fi/~jkorpela/forms/cgic.html
*/

/* sample request line for echo.cgi
POST /cgi-bin/echo.cgi HTTP/1.0
Content-Type: application/x-www-form-urlencoded
Content-Length: 20

data=test&data2=echo
*/

/* compile 
gcc -o echo.cgi echo.cpp
*/

int main(int argc, char* argv[]){
	char *lenstr;
	char *scriptName;
	char *serverName;
	char *serverPort;
	char *httpFrom;
	char *httpAgent;
	char input[MAXLEN];
	long len;
	//comment the following printf if your server is handling this header 
	// printf("%s%c%c\n", "Content-Type:text/html",13,10);
	
	printf("<H1>echo response</H1>\n");
	lenstr = getenv("CONTENT_LENGTH");
	scriptName = getenv("SCRIPT_NAME");
	serverName = getenv("SERVER_NAME");
	serverPort = getenv("SERVER_PORT");
	httpFrom = getenv("HTTP_FROM");
	httpAgent = getenv("HTTP_USER_AGENT");
	if(lenstr == NULL || sscanf(lenstr,"%ld",&len)!=1 || len > MAXLEN)
		printf("<P>Error in invocation - wrong FORM probably.</P>");
	else {
		fprintf(stderr, "THIS IS AN ERROR!!\n");
		printf("<P>Script Name is: %s</P>\n", scriptName);
		printf("<P>Server name is: %s</P>\n", serverName);
		printf("<P>Server port is: %s</P>\n", serverPort);
		printf("<P>HTTP FROM is: %s</P>\n", httpFrom);
		printf("<P>user Agent is: %s</P>\n", httpAgent);
		printf("<P>content length is: %ld</P>\n", len);
		fgets(input, len+1, stdin);
		printf("<P>content sent is: %s</P>", input);
	}
	return 0;
}
