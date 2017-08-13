#!/usr/bin/env python

import os
import sys
import time
import cgi
import datetime
from urlparse import parse_qs
import Cookie

def getCookieData(cookie):
    try:
        params = parse_qs(cookie)
        good_dict = {}
        for d in params:
            good_dict[d.strip()] = params[d]
        # params = dict(params)
        return good_dict
    except Exception as e:
        return None

ZERO = datetime.timedelta(0)

class UTC(datetime.tzinfo):
    def utcoffset(self, dt):
        return ZERO

    def tzname(self, dt):
        return "UTC"

    def dst(self, dt):
        return ZERO

def writeHTML(content,cookie=None):
    html_message = ""
    if(cookie != None):
        html_message += cookie
    html_message += "<html>\n<body>" + content + "</body>\n</html>\n"
    sys.stdout.write(html_message)

try:
    method = os.environ.get("REQUEST_METHOD")
    content_length = int(os.environ.get("CONTENT_LENGTH", ""))
except ValueError:
    pass

cookie_str = None
try:
    cookie_str = os.environ.get("HTTP_COOKIE")
except:
    pass

def prices(item):
    if item == "Toyota":
        return 100
    if item == "Ford":
        return 200
    if item == "Tesla":
        return 300
    if item == "Subaru":
        return 400
    return 0

def getDropDown(name, cart=None):
    items = set(["Toyota", "Ford", "Tesla", "Subaru"])
    if cart != None:
        cart = set(cart)
        items = items - cart
    html = "<p> Greetings <b>" + name + "</b> here are some items to buy </p>"
    html +="""
<form action="/cgi-bin/store.cgi" method="post">
  <select name="item">"""
    for i in items:
        html += "<option value=\"" + i + "\">" + i + " $" + str(prices(i)) + "</option>"
    html += """</select>
  <input type="submit" name="action" value="Add To Cart">
</form>
"""
    if cart != None:
        html += """<p> Your Cart <p>
        <table border=\"1\">
      <tr>
        <th>Item</th>
        <th>Price</th> 
        <th>Remove</th>
      </tr>
      
      """
        total = 0
        for i in cart:
            price = prices(i)
            total += price
            html += "<tr><td>" + i + "</td>"
            html += "<td>$" + str(price) + "</td>"
            html += """<td>
            <form action="/cgi-bin/store.cgi" method="post">
          <input type="hidden" name="item" value=\"""" + i + """\">
          <input type="submit"name="action" value="Remove From Cart">
        </form></td></tr>
        """
        html += "</table><p>Total: $" + str(total) + "</p>"
    else:
        html += "<p> Cart Empty</p>"
    return html

def getLogin():
    html = """
    <form action="/cgi-bin/store.cgi" method="post">
  Name:<br>
  <input type="text" name="name">
  <br>
  password:<br>
  <input type="text" name="password">
  <br><br>
  <input type="submit" name="action" value="Submit">
    </form> 
    """
    return html

session = getCookieData(cookie_str)

if method == "POST":
    cookie = None
    if content_length > 0:
        payload = sys.stdin.read(content_length)
        form = dict(cgi.parse_qs(payload))
        action = form['action'][0] if 'action' in form else None
        if session == None or (session != None and 'name' not in session):
            name = form['name'] if 'name' in form else None
            if name != None and 'password' in form and action == 'Submit':
                cookie = "Set-Cookie: cart=\r\n"
                name = name[0]
                utc = UTC()
                date = datetime.datetime.now(utc) + datetime.timedelta(minutes = 3)
                date_str = datetime.datetime.strftime(date, '%a, %d %b %Y %H:%M:%S GMT')
                cookie += "Set-Cookie: name=" + name + "; expires=" + date_str + "\r\n"
                html = getDropDown(name)
            else:
                html = getLogin()
        else:
            name = session['name'][0] if 'name' in session else None
            if name != None and (action == 'Add To Cart' or action == 'Remove From Cart') and 'item' in form:
                item = form['item'][0]
                cart = session['cart'][0] if 'cart' in session else None
                if cart != None:
                    cart_items = cart.split(",")
                    if action == 'Add To Cart':
                        cart_items.append(item)
                    else:
                        cart_items = set(cart_items) - set([item])
                else:
                    cart_items = [item]
                cookie = "Set-Cookie: cart=" + ",".join(cart_items) + "\r\n"
                html = getDropDown(name,cart_items)
            elif name != None:
                cart = session['cart'][0] if 'cart' in session else None
                cart_items = None
                if cart != None:
                    cart_items = cart.split(",")
                html = getDropDown(name,cart_items)
            else:
                html = getLogin()

    else:
        html = getLogin()
    writeHTML(html,cookie=cookie)
    sys.exit()

# Fix no name
if method == "GET" and session is not None:
    if 'name' in session:
        name = session['name'][0]
        cart = session['cart'][0] if 'cart' in session else None
        cart_items = None
        if cart != None:
            cart_items = cart.split(",")
        html = getDropDown(name,cart_items)
    else:
        html = getLogin()
    writeHTML(html)
    sys.exit()



if session is None:
    html = getLogin()
    writeHTML(html)
    sys.exit()




