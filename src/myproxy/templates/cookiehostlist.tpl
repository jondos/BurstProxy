<form action="$ACTION" method="post">
<table border="1" cellspacing="0" class="rules">
<tr><td><b>$TPL_PATTERN</b></td><td align="center"><a href="/cookierules#exacthelp" class="local"><b>$TPL_EXACTMATCH</b></a></td><td align="center"><b>$TPL_DELETE</b></td></tr>
$ROWS
<tr><td colspan="2">&nbsp;</td><td align="center"><button type="submit" name="action" value="delete">$TPL_DELETE</button></td></tr>
<tr><td><a name="add"><b>$TPL_PATTERN</b></a></td><td align="center"><a href="/cookierules#exacthelp" class="local"><b>$TPL_EXACTMATCH</b></a></td><td>&nbsp;</td></tr>
<tr><td><input type="text" name="host" value="$ADDHOST" class="ruletext"></td><td align="center"><input type="checkbox" name="exact" value="true"></td><td align="center"><button type="submit" name="action" value="add">$TPL_ADD</button></td></tr>
</table>
</form>