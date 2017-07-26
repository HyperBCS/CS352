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
	char input[MAXLEN];
	long len;
	//comment the following printf if your server is handling this header 
	printf("%s%c%c\n", "Content-Type:text/html",13,10);
	
	printf("<H1>echo response</H1>\n");
	lenstr = getenv("CONTENT_LENGTH");
	if(lenstr == NULL || sscanf(lenstr,"%ld",&len)!=1 || len > MAXLEN)
		printf("<P>Error in invocation - wrong FORM probably.</P>");
	else {
		printf("<P>content length is: %ld</P>", len);
		fgets(input, len+1, stdin);
		printf("<P>content sent is: %s</P>", input);
	}
	return 0;
}
