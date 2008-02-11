<h2>$TPL_BLOCKRULES $SAVED</h2>

$RULES

<hr>

<h2>$TPL_HELP</h2>

Every request the browser sends through the proxy has its host and path part
compared to the list of block rules. If it matches a rule in both parts (at
least one part must be specified for each rule, empty parts always match),
the request will be blocked. You can use anything that complies with the
<a href="http://java.sun.com/j2se/1.4.1/docs/api/java/util/regex/Pattern.html#sum" target="_blank">Java 1.4 regular expression syntax</a>.
The path part includes query and anchor string, if present.<br>
<br>
<b>Example:</b><br>
<br>
<table border="1" cellspacing="0" cellpadding="5">
<tr><td><b>Request</b></td><td>http://www.company.foo/shop/search?name=foo</td></tr>
<tr><td><b>$TPL_HOSTPART</b></td><td>www.company.foo</td></tr>
<tr><td><b>$TPL_PATHPART</b></td><td>/shop/search?name=foo</td></tr>
</table>
<br>
If you're not familiar with regular expressions, there is plenty of information
in the
<a href="http://directory.google.com/Top/Computers/Programming/Languages/Regular_Expressions/FAQs,_Help,_and_Tutorials/" target="_blank">Google directory for regular expressions</a>.