param(
    [int]$Count = 1000,
    [string]$Hostname = "localhost",
    [int]$Port = 6666,
    [int]$StartId = 1
)

Write-Host "Sending $Count messages to $Hostname`:$Port..." -ForegroundColor Green

$tcpClient = $null
$writer = $null
$reader = $null

try {
    $tcpClient = New-Object System.Net.Sockets.TcpClient($Hostname, $Port)
    $stream = $tcpClient.GetStream()
    $writer = New-Object System.IO.StreamWriter($stream)
    $writer.AutoFlush = $true
    $reader = New-Object System.IO.StreamReader($stream)
    
    $successCount = 0
    $failCount = 0

    for ($i = $StartId; $i -lt ($StartId + $Count); $i++) {
        try {
            $message = "SET $i Message_$i"
            $writer.WriteLine($message)
            
            $response = $reader.ReadLine()
            
            if ($response -like "OK*") {
                $successCount++
                if ($i % 100 -eq 0) {
                    Write-Host "Progress: $i/$($StartId + $Count - 1) - Success: $successCount, Failed: $failCount" -ForegroundColor Cyan
                }
            } else {
                $failCount++
                Write-Host "Failed at message $i : $response" -ForegroundColor Red
            }
        }
        catch {
            $failCount++
            Write-Host "Error at message $i : $_" -ForegroundColor Red
        }
    }
}
catch {
    Write-Host "Connection failed: $_" -ForegroundColor Red
    $failCount = $Count
}
finally {
    if ($writer) { $writer.Close() }
    if ($reader) { $reader.Close() }
    if ($tcpClient) { $tcpClient.Close() }
}

Write-Host "`nCompleted!" -ForegroundColor Green
Write-Host "Success: $successCount" -ForegroundColor Green
Write-Host "Failed: $failCount" -ForegroundColor Red
