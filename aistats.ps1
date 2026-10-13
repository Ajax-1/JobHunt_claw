# AI code statistics script
# Usage: .\aistats.ps1 [-Days <number>]

param(
    [Parameter(Mandatory=$false)]
    [int]$Days = 30  # Default to last 30 days
)

# Calculate the date threshold
$thresholdDate = (Get-Date).AddDays(-$Days)
$thresholdDateStr = $thresholdDate.ToString("yyyy-MM-dd")

Write-Host "Statistics for the last $Days days (since $thresholdDateStr)" -ForegroundColor Cyan

# Get commits within the specified time range
$commits = git log --all --since="$thresholdDateStr" --pretty=format:"%H|%an|%ae" --encoding=UTF-8

# Initialize counters
$aiCommits = @()
$humanCommits = @()
$aiTotalLines = 0
$humanTotalLines = 0

foreach ($commit in $commits) {
    $parts = $commit -split "\|"
    $commitHash = $parts[0]
    $authorName = $parts[1]

    # Check if it's an AI commit (starts with AI.)
    if ($authorName -match "^AI\.") {
        $aiCommits += $commitHash
    } else {
        $humanCommits += $commitHash
    }
}

# Calculate lines for AI commits
foreach ($commitHash in $aiCommits) {
    $lines = git diff-tree --no-commit-id --numstat $commitHash
    foreach ($line in $lines) {
        $parts = $line -split "\s+"
        if ($parts.Length -ge 2) {
            if ($parts[0] -match "^\d+$") {
                $aiTotalLines += [int]$parts[0]
            }
        }
    }
}

# Calculate lines for human commits
foreach ($commitHash in $humanCommits) {
    $lines = git diff-tree --no-commit-id --numstat $commitHash
    foreach ($line in $lines) {
        $parts = $line -split "\s+"
        if ($parts.Length -ge 2) {
            if ($parts[0] -match "^\d+$") {
                $humanTotalLines += [int]$parts[0]
            }
        }
    }
}

# Calculate totals
$totalCommits = $aiCommits.Count + $humanCommits.Count
$totalLines = $aiTotalLines + $humanTotalLines

# Calculate percentages
$aiCommitPercentage = if ($totalCommits -gt 0) { [math]::Round(($aiCommits.Count / $totalCommits) * 100, 2) } else { 0 }
$humanCommitPercentage = if ($totalCommits -gt 0) { [math]::Round(($humanCommits.Count / $totalCommits) * 100, 2) } else { 0 }
$aiLinesPercentage = if ($totalLines -gt 0) { [math]::Round(($aiTotalLines / $totalLines) * 100, 2) } else { 0 }
$humanLinesPercentage = if ($totalLines -gt 0) { [math]::Round(($humanTotalLines / $totalLines) * 100, 2) } else { 0 }

# Display results
Write-Host "`n========== AI Code Statistics ==========" -ForegroundColor Cyan
Write-Host "`nCommit Statistics:" -ForegroundColor Yellow
Write-Host "  Total Commits: $totalCommits"
Write-Host "  AI Commits: $($aiCommits.Count) ($aiCommitPercentage%)" -ForegroundColor Green
Write-Host "  Human Commits: $($humanCommits.Count) ($humanCommitPercentage%)" -ForegroundColor Blue

Write-Host "`nCode Lines Statistics:" -ForegroundColor Yellow
Write-Host "  Total Lines: $totalLines"
Write-Host "  AI Generated Lines: $aiTotalLines ($aiLinesPercentage%)" -ForegroundColor Green
Write-Host "  Human Written Lines: $humanTotalLines ($humanLinesPercentage%)" -ForegroundColor Blue

Write-Host "`nAI Code Generation Ratio:" -ForegroundColor Yellow
Write-Host "  By Commits: $aiCommitPercentage% of total commits" -ForegroundColor Green
Write-Host "  By Lines: $aiLinesPercentage% of total code" -ForegroundColor Green

Write-Host "`n========================================`n" -ForegroundColor Cyan
