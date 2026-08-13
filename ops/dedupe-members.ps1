# Removes duplicate Java member declarations (fields or interface methods), keeping the FIRST
# occurrence and deleting later ones together with their immediately preceding annotation/comment lines.
#
# Usage: .\dedupe-members.ps1 -Path <file.java> -Names @('gatewayTxnId','settledAt')
param(
  [Parameter(Mandatory=$true)][string]$Path,
  [Parameter(Mandatory=$true)][string[]]$Names
)

$lines = [System.Collections.Generic.List[string]]([string[]](Get-Content -LiteralPath $Path))

foreach ($name in $Names) {
  # collect declaration line indices for this member
  $hits = @()
  for ($i = 0; $i -lt $lines.Count; $i++) {
    if ($lines[$i] -match ("(?:private|protected|public)?\s*[\w<>\[\],\.\s]+\s" + [regex]::Escape($name) + "\s*(?:;|\()")) {
      $hits += $i
    }
  }
  if ($hits.Count -le 1) { Write-Host "  $name : $($hits.Count) decl, nothing to remove"; continue }

  # remove every occurrence after the first, deepest-first
  $toRemove = $hits[1..($hits.Count - 1)]
  [array]::Reverse($toRemove)
  foreach ($idx in $toRemove) {
    $start = $idx
    # walk back over contiguous annotations / comments / blank lines that belong to this decl
    while ($start -gt 0) {
      $prev = $lines[$start - 1].Trim()
      if ($prev -like '@*' -or $prev -like '//*' -or $prev -like '*/' -or $prev -like '/**' -or $prev -like '*' -or $prev -eq '') {
        if ($prev -eq '' -and $start - 1 -gt 0) {
          $before = $lines[$start - 2].Trim()
          if (-not ($before -like '@*' -or $before -like '//*')) { break }
        }
        $start--
      } else { break }
    }
    $count = $idx - $start + 1
    $lines.RemoveRange($start, $count)
  }
  Write-Host "  $name : removed $($toRemove.Count) duplicate decl(s)"
}

[System.IO.File]::WriteAllLines((Resolve-Path -LiteralPath $Path), $lines)
$t = Get-Content -LiteralPath $Path -Raw
$delta = ([regex]::Matches($t, '\{')).Count - ([regex]::Matches($t, '\}')).Count
Write-Host "$Path -> brace delta: $delta"
