<form action="$ACTION" method="post">
<table border="1" cellspacing="0" class="rules">
<tr><td><b>$TPL_HOSTPART</b></td><td><b>$TPL_PATHPART</b></td><td><b>$TPL_COMMENT</b></td><td align="center"><b>$TPL_DELETE</b></td><td>&nbsp;</td></tr>
$ROWS
<tr><td colspan="3">&nbsp;</td><td align="center"><button type="submit" name="action" value="delete">$TPL_DELETE</button></td><td>&nbsp;</td></tr>
<tr><td colspan="5">&nbsp;</td></tr>
<tr><td><input type="text" name="hostpart" class="ruletext"></td><td><input type="text" name="pathpart" class="ruletext"></td><td><input type="text" name="comment" class="ruletext"></td><td align="center"><button type="submit" name="action" value="add">$TPL_ADD</button></td><td>&nbsp;</td></tr>
</table>
</form>