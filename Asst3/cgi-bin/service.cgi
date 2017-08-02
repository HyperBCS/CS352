#!/usr/bin/env python

import os
import sys
import cgi
import hashlib
from io import BytesIO as IO

def writeHTML(content):
    sys.stdout.write("<html>\n<body>" + content + "</body>\n</html>")

content_length = 0
try:
    method = os.environ.get("REQUEST_METHOD")
    content_length = int(os.environ.get("CONTENT_LENGTH", ""))
except ValueError:
    pass

payload = sys.stdin.read(content_length)

if method == "GET":
    html = """
    <form action=\"/cgi-bin/service.cgi\"
    enctype=\"multipart/form-data\" method=\"post\">
    <p>
    Enter MD5:<br>
    <input type=\"text\" name=\"md5input\" size="30">
    </p>
    <p>
    Please specify a file, or a set of files:<br>
    <input type=\"file\" name=\"datafile\" size=\"40\">
    </p>
    <div>
    <input type=\"submit\" value=\"Send\">
    </div>
    </form>
    """

    writeHTML(html)
    sys.exit()

b = payload.split("\n")
if len(b) > 1:
    b = b[0][2:]
parsed = cgi.FieldStorage(IO(payload),
    headers={'content-type': 'multipart/form-data; boundary=' + b + ';',
    'content-length': len(payload)},
    environ={'REQUEST_METHOD': 'POST'})

md5sum = parsed.getfirst("md5input", "")
file = parsed.getfirst("datafile", "")
if(len(md5sum) == 0 or len(file) == 0):
    writeHTML(html)
    sys.exit()
md5good = hashlib.md5(file).hexdigest()
if md5good.lower() == md5sum.lower():
    string = "The hashes match."
else:
    string = md5sum.lower() + " DOES NOT MATCH " + md5good
writeHTML(string)
# writeHTML(str(parsed))
#encode('utf-8')
