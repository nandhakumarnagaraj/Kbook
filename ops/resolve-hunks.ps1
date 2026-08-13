# Resolve git conflict hunks in a file by hunk index (1-based).
#
# Rule values:
#   'ours'                -> keep HEAD side only
#   'theirs'              -> keep incoming side only
#   'both'                -> ours then theirs, concatenated
#   'bridge:<a>|<b>|...'  -> ours, then the literal bridge lines, then theirs.
#                            Use \t in bridge lines for tabs.
#
# Usage:
#   .\resolve-hunks.ps1 -Path <file> -Rules @{ 1='ours'; 3="bridge:\t\t\t\t\t\t}" }
# Unlisted hunks default to 'ours'.
param(
  [Parameter(Mandatory=$true)][string]$Path,
  [Parameter(Mandatory=$true)][hashtable]$Rules
)

$lines = [string[]](Get-Content -LiteralPath $Path)
$out = New-Object 'System.Collections.Generic.List[string]'
$idx = 0
$i = 0

while ($i -lt $lines.Count) {
  if ($lines[$i] -match '^<<<<<<<') {
    $idx++
    $ours = New-Object 'System.Collections.Generic.List[string]'
    $theirs = New-Object 'System.Collections.Generic.List[string]'
    $i++
    while ($i -lt $lines.Count -and $lines[$i] -notmatch '^=======') { $ours.Add([string]$lines[$i]); $i++ }
    $i++
    while ($i -lt $lines.Count -and $lines[$i] -notmatch '^>>>>>>>') { $theirs.Add([string]$lines[$i]); $i++ }
    $i++

    $rule = if ($Rules.ContainsKey($idx)) { [string]$Rules[$idx] } else { 'ours' }

    if ($rule -eq 'ours') {
      foreach ($l in $ours) { $out.Add($l) }
    } elseif ($rule -eq 'theirs') {
      foreach ($l in $theirs) { $out.Add($l) }
    } elseif ($rule -eq 'both') {
      foreach ($l in $ours) { $out.Add($l) }
      foreach ($l in $theirs) { $out.Add($l) }
    } elseif ($rule.StartsWith('bridge:')) {
      foreach ($l in $ours) { $out.Add($l) }
      $bridge = $rule.Substring(7)
      foreach ($b in ($bridge -split '\|')) { $out.Add([string]($b -replace '\\t', "`t")) }
      foreach ($l in $theirs) { $out.Add($l) }
    } else {
      throw "Unknown rule '$rule' for hunk $idx in $Path"
    }
  } else {
    $out.Add([string]$lines[$i])
    $i++
  }
}

[System.IO.File]::WriteAllLines((Resolve-Path -LiteralPath $Path), $out)
$left = (Select-String -Path $Path -Pattern '^(<<<<<<<|=======|>>>>>>>)' | Measure-Object).Count
Write-Host "$Path : resolved $idx hunk(s), markers left: $left"
