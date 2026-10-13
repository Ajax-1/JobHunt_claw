# Git commit script for AI-generated content
# Usage: .\aigit.ps1 -c "your commit message"

param(
    [Parameter(Mandatory=$true)]
    [string]$c
)

# Get current git user.name with UTF-8 encoding
$originalUserName = git config user.name
# Ensure proper encoding for Chinese characters
$originalUserName = [System.Text.Encoding]::UTF8.GetString([System.Text.Encoding]::Default.GetBytes($originalUserName))

Write-Host "Current username: $originalUserName" -ForegroundColor Cyan

# Check if already starts with AI.
if ($originalUserName -match "^AI\.") {
    Write-Host "Error: Current user is already AI user ($originalUserName), please switch to original user first" -ForegroundColor Red
    exit 1
}

# Set new user.name with AI. prefix
$newUserName = "AI.$originalUserName"
Write-Host "Setting temp username to: $newUserName" -ForegroundColor Yellow
git config user.name $newUserName

# Execute git add
Write-Host "`nAdding files to staging area..." -ForegroundColor Cyan
git add .
if ($LASTEXITCODE -ne 0) {
    Write-Host "Error: git add failed" -ForegroundColor Red
    git config user.name $originalUserName
    exit 1
}

# Add Co-Authored-By information
$coAuthor = "Co-Authored-By: AI-Assistant"
$fullCommitMessage = "$c`n`n$coAuthor"

# Execute git commit
Write-Host "`nCommitting code..." -ForegroundColor Cyan
Write-Host "Commit message: $c" -ForegroundColor Green
Write-Host "Co-author: $coAuthor" -ForegroundColor Green

# Save commit message to temp file with UTF-8 encoding to avoid pipe encoding issues
$tempFile = [System.IO.Path]::GetTempFileName()
[System.IO.File]::WriteAllText($tempFile, $fullCommitMessage, [System.Text.UTF8Encoding]::new($false))
git commit -F $tempFile --cleanup=strip
Remove-Item $tempFile -Force

# Restore original user.name immediately after commit to avoid any encoding issues
Write-Host "`nRestoring original username to: $originalUserName" -ForegroundColor Yellow
git config user.name $originalUserName

if ($LASTEXITCODE -eq 0) {
    Write-Host "`nCommit successful!" -ForegroundColor Green
    Write-Host "`nDone!" -ForegroundColor Green
} else {
    Write-Host "`nCommit failed!" -ForegroundColor Red
    exit 1
}
