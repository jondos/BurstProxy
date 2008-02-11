<h2>$TPL_COOKIERULES $SAVED</h2>

$RULES

<hr>

<h2>$TPL_HELP</h2>

The decision whether your browser may send cookies to a server is made by
comparing the hostname to a list of simple patterns. If a pattern matches
the right-hand side of the hostname, the cookie is passed on.<br>
<br>
<b>Example:</b><br>
<br>
<table border="1" cellspacing="0" cellpadding="5">
<tr><td><b>$TPL_PATTERN</b></td><td align="right">company.foo</td><td align="center"><b>Matches</b></td></tr>
<tr><td><b>$TPL_HOST</b></td><td align="right">www.company.foo</td><td align="center">Yes</td></tr>
<tr><td><b>$TPL_HOST</b></td><td align="right">department.company.foo</td><td align="center">Yes</td></tr>
<tr><td colspan="3">&nbsp;</td></tr>
<tr><td><b>$TPL_PATTERN</b></td><td align="right">department.company.foo</td><td align="center"><b>Matches</b></td></tr>
<tr><td><b>$TPL_HOST</b></td><td align="right">company.foo</td><td align="center">No</td></tr>
<tr><td><b>$TPL_HOST</b></td><td align="right">department.company.foo</td><td align="center">Yes</td></tr>
<tr><td><b>$TPL_HOST</b></td><td align="right">www.department.company.foo</td><td align="center">Yes</td></tr>
</table>
<br>
Incoming cookies have their domain value compared to the pattern list. If the
domain value matches the right-hand side of a pattern, the cookie is passed
on to the browser, if it is longer than the pattern, the cookie is denied.
This is because according to the
<a href="http://www.netscape.com/newsref/std/cookie_spec.html" target="_blank">cookie specification</a>,
<tt>company.foo</tt> is a valid cookie domain for the host
<tt>www.company.foo</tt>.<br>
<br>
<b>Note:</b> this means that a pattern like <tt>company.foo</tt> will not
allow a cookie with a domain value of <tt>server.company.foo</tt> to pass !<br>
<br>
If you need a little more flexibility, you can use wildcards within the parts
of a pattern. The <b><tt>*</tt></b> matches any number of characters, while
<b><tt>?</tt></b> matches a single character.<br>

<a name="exacthelp"><h3>$TPL_EXACTMATCH</h3></a>
For outgoing cookies, a pattern like <tt>company.foo</tt> will allow cookies to
be sent to <tt>www.company.foo</tt>, because there is a right-hand side match.
If you want to specifically restrict the match to <tt>company.foo</tt>, select
the "$TPL_EXACTMATCH" option when adding the pattern.<br>
<br>
<b>Example:</b><br>
<br>
<table border="1" cellspacing="0" cellpadding="5">
<tr><td><b>$TPL_PATTERN</b></td><td align="right">department.company.foo</td><td align="center"><b>Matches</b></td></tr>
<tr><td><b>$TPL_HOST</b></td><td align="right">company.foo</td><td align="center">No</td></tr>
<tr><td><b>$TPL_HOST</b></td><td align="right">department.company.foo</td><td align="center">Yes</td></tr>
<tr><td><b>$TPL_HOST</b></td><td align="right">ads.department.company.foo</td><td align="center">No</td></tr>
</table>