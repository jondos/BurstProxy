<h2>$TPL_SETTINGS $SAVED</h2>

<form action="/settings" method="post">
<table border="1" cellspacing="0" class="settings">
<tr><td>
<a href="#cookiehelp" class="local">Cookie Handling</a><br>
<br>
$COOKIE_ROWS
</td></tr>

<tr><td>
<a href="#refererhelp" class="local">Referer Handling</a><br>
<br>
$REFERER_ROWS
</td></tr>

<tr><td>
<a href="#fromhelp" class="local">From Handling</a><br>
<br>
$FROM_ROWS
<br>
<b>Fake:</b>&nbsp;<input type="text" name="from.value" value="$FROM_VALUE" size="50"><br>
</td></tr>

<tr><td>
<a href="#agenthelp" class="local">User-Agent Handling</a><br>
<br>
$AGENT_ROWS
<br>
<b>Fake:</b>&nbsp;<input type="text" name="agent.value" value="$AGENT_VALUE" size="50"><br>
</td></tr>

<tr><td>
<a href="#prefetchinghelp" class="local">Prefetching</a><br>
<br>
$PREFETCHING_ROWS
<br>
<b>Remote Address:</b>&nbsp;<input type="text" name="prefetching.remoteaddress" value="$PREFETCHING_REMOTEADDRESS" size="50"><br>
</td></tr>

<tr><td valign="middle">
<button type="submit">$TPL_SAVE</button>&nbsp;<button type="reset">$TPL_RESET</button>
</td></tr>
</table>
</form>

<hr>

<h2>$TPL_HELP</h2>

<a name="cookiehelp"><h3>Cookie Handling</h3></a>
This option decides whether the proxy forwards or denies cookies (in both
directions). It is possible to allow cookies based on a set of rules configured
on the <a href="/cookierules" class="local">$TPL_COOKIERULES</a> page.<br>
<br>
If you need to learn more about cookies, please consult your browser's
documentation, or the
<a href="http://directory.google.com/Top/Computers/Security/Internet/Privacy/Cookies/" target="_blank">Google directory for cookies</a>.<br>

<a name="refererhelp"><h3>Referer Handling</h3></a>
This option defines what happens to the <b>Referer</b> (sic) header when sent
by your browser. In addition to allowing or removing the complete header, the
proxy can change it to the root directory of the <b>destination</b> server.
This comes in handy when a server controls access to images by checking the
referer header, for example.<br>
<br>
The referer header tells the web server which page you visited prior to your
current request. One is usually sent when clicking on a link. Since this works
across websites, you might not want to send a referer header to every web
server out there.<br>

<a name="fromhelp"><h3>From Handling</h3></a>
The <b>From</b> header, in case it is sent by your browser, may contain your
e-mail address. You can remove the header altogether or send a humorous fake
one.<br>

<a name="agenthelp"><h3>User-Agent Handling</h3></a>
The <b>User-Agent</b> header may contain information about your browser,
operating system, computer platform and other things. This information can
be used by the website to serve customized web pages. While this header is
harmless most of the time, you can optionally have it removed or send
completely made up information to the server.<br>