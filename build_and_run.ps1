<#
.SYNOPSIS
Builds and runs the Java CFDS project.

.DESCRIPTION
This script automatically downloads a portable Maven distribution if it's not installed,
compiles the Java source code leveraging the JavaCV framework (for bundled OpenCV DLLs),
builds a FAT executable JAR, and finally runs the program.
#>

$ErrorActionPreference = "Stop"

$MavenVer = "3.9.6"
$MavenDir = "$PSScriptRoot\.maven\apache-maven-$MavenVer"
$MavenZip = "$PSScriptRoot\.maven\maven.zip"

if (!(Test-Path $MavenDir)) {
    Write-Host "Maven not found. Downloading portable Apache Maven $MavenVer..." -ForegroundColor Cyan
    New-Item -ItemType Directory -Force -Path "$PSScriptRoot\.maven" | Out-Null
    
    $Url = "https://archive.apache.org/dist/maven/maven-3/$MavenVer/binaries/apache-maven-$MavenVer-bin.zip"
    Invoke-WebRequest -Uri $Url -OutFile $MavenZip
    
    Write-Host "Extracting Maven..." -ForegroundColor Cyan
    Expand-Archive -Path $MavenZip -DestinationPath "$PSScriptRoot\.maven" -Force
    Remove-Item -Path $MavenZip -Force
}

$MvnBinary = "$MavenDir\bin\mvn.cmd"
$TempDir = "$PSScriptRoot\temp"
if (!(Test-Path $TempDir)) { New-Item -ItemType Directory -Force -Path $TempDir | Out-Null }

Write-Host "Building Java CFDS Backend (Downloading OpenCV Native Binaries, SQLite JDBC, Gson)..." -ForegroundColor Yellow
Write-Host "NOTE: Redirecting library tracking and TEMP folders to D: drive due to full C: drive." -ForegroundColor Cyan
Write-Host "This may take a few minutes on the very first run..." -ForegroundColor DarkGray

# Force Maven to use custom settings and custom temp directory to avoid C: drive entirely
$env:MAVEN_OPTS = "-Djava.io.tmpdir=`"$TempDir`""
& $MvnBinary clean compile assembly:single -s "$PSScriptRoot\settings.xml"

if ($LASTEXITCODE -ne 0) {
    Write-Host "Build Failed!" -ForegroundColor Red
    exit $LASTEXITCODE
}

Write-Host "Build Successful! Running CFDS..." -ForegroundColor Green
Write-Host "==========================================" -ForegroundColor Cyan

$TargetJar = "$PSScriptRoot\target\cfds-1.0-SNAPSHOT-jar-with-dependencies.jar"

# Run it!
$env:JAVA_TOOL_OPTIONS = "-Dorg.bytedeco.javacpp.cachedir=`"$PSScriptRoot\.javacpp_cache`""
java -jar $TargetJar -h
