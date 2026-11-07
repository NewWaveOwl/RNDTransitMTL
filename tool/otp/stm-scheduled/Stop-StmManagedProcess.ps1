function Stop-StmManagedProcess {
    param([Parameter(Mandatory=$true)][int]$ProcessId)
    # Callers must first verify that this PID belongs to their OTP/adapter server.
    $handle = Get-Process -Id $ProcessId -ErrorAction SilentlyContinue
    if (!$handle) { return }
    try {
        if ($handle.HasExited) { return }
        try {
            Stop-Process -InputObject $handle -Force -ErrorAction Stop
        } catch {
            $handle.Refresh()
            if (!$handle.HasExited) { throw }
        }
        if (!$handle.WaitForExit(20000)) {
            throw "Managed server PID $ProcessId did not terminate within 20 seconds."
        }
        # An exited process can remain discoverable while another process holds
        # its handle. HasExited/WaitForExit are authoritative, not Get-Process.
    } finally {
        $handle.Dispose()
    }
}
