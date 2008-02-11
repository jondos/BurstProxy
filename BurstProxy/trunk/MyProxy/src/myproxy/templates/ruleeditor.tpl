<h2>$SECTION</h2>

<form action="$ACTION" method="post">
<table border="1" cellspacing="0" class="rules">
<tr>
<td><b>$TPL_HOSTPART</b></td>
<td><b>$TPL_PATHPART</b></td>
<td><b>$TPL_COMMENT</b></td>
<td>&nbsp;</td>
</tr>
<tr>
<td><input type="text" class="ruletext" name="hostpart" value="$HOSTPART"></td>
<td><input type="text" class="ruletext" name="pathpart" value="$PATHPART"></td>
<td><input type="text" class="ruletext" name="comment" value="$COMMENT"></td>
<td align="center"><button type="submit" name="action" value="replace">$TPL_SAVE</button>&nbsp;<button type="reset">$TPL_RESET</button></td>
</tr>
</table>
<input type="hidden" name="id" value="$ID">
</form>