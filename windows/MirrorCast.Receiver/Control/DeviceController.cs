using System.Diagnostics;
using System.IO;

namespace MirrorCast.Receiver.Control;

public class DeviceController
{
    private string? _adbPath;
    private readonly object _lock = new();

    public DeviceController()
    {
        FindAdb();
    }

    private void FindAdb()
    {
        var candidates = new[]
        {
            Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), @"Android\Sdk\platform-tools\adb.exe"),
            @"C:\Users\91808\AppData\Local\Android\Sdk\platform-tools\adb.exe",
            @"C:\Android\platform-tools\adb.exe",
            "adb.exe"
        };

        foreach (var path in candidates)
        {
            if (File.Exists(path))
            {
                _adbPath = path;
                return;
            }
        }

        _adbPath = "adb";
    }

    public void Tap(int x, int y)
    {
        RunAdbAsync($"shell input tap {x} {y}");
    }

    public void Swipe(int x1, int y1, int x2, int y2, int durationMs = 250)
    {
        RunAdbAsync($"shell input swipe {x1} {y1} {x2} {y2} {durationMs}");
    }

    public void PressBack()
    {
        RunAdbAsync("shell input keyevent 4"); // KEYCODE_BACK
    }

    public void PressHome()
    {
        RunAdbAsync("shell input keyevent 3"); // KEYCODE_HOME
    }

    public void PressRecents()
    {
        RunAdbAsync("shell input keyevent 187"); // KEYCODE_APP_SWITCH
    }

    public void PressPower()
    {
        RunAdbAsync("shell input keyevent 26"); // KEYCODE_POWER
    }

    public void VolumeUp()
    {
        RunAdbAsync("shell input keyevent 24"); // KEYCODE_VOLUME_UP
    }

    public void VolumeDown()
    {
        RunAdbAsync("shell input keyevent 25"); // KEYCODE_VOLUME_DOWN
    }

    public void SendText(string text)
    {
        var escaped = text.Replace(" ", "%s").Replace("\"", "\\\"");
        RunAdbAsync($"shell input text \"{escaped}\"");
    }

    public string? TargetDeviceSerial { get; set; }

    public List<string> GetConnectedDevices()
    {
        var devices = new List<string>();
        try
        {
            var psi = new ProcessStartInfo
            {
                FileName = _adbPath ?? "adb",
                Arguments = "devices",
                UseShellExecute = false,
                CreateNoWindow = true,
                RedirectStandardOutput = true
            };
            using var proc = Process.Start(psi);
            if (proc != null)
            {
                string? line;
                while ((line = proc.StandardOutput.ReadLine()) != null)
                {
                    line = line.Trim();
                    if (string.IsNullOrEmpty(line) || line.StartsWith("List of") || line.StartsWith("*"))
                        continue;
                    var parts = line.Split('\t', StringSplitOptions.RemoveEmptyEntries);
                    if (parts.Length >= 2 && parts[1] == "device")
                    {
                        devices.Add(parts[0]);
                    }
                }
                proc.WaitForExit(1000);
            }
        }
        catch { }
        return devices;
    }

    public void SetupAllUsbReverseTunnels()
    {
        Task.Run(() =>
        {
            var devices = GetConnectedDevices();
            if (devices.Count == 0)
            {
                RunSingleAdb("reverse tcp:8090 tcp:8090");
                RunSingleAdb("reverse tcp:8091 tcp:8091");
                return;
            }

            foreach (var serial in devices)
            {
                RunSingleAdb($"-s {serial} reverse tcp:8090 tcp:8090");
                RunSingleAdb($"-s {serial} reverse tcp:8091 tcp:8091");
            }
        });
    }

    private void RunSingleAdb(string arguments)
    {
        try
        {
            var psi = new ProcessStartInfo
            {
                FileName = _adbPath ?? "adb",
                Arguments = arguments,
                UseShellExecute = false,
                CreateNoWindow = true,
                RedirectStandardOutput = true,
                RedirectStandardError = true
            };
            using var proc = Process.Start(psi);
            proc?.WaitForExit(2000);
        }
        catch { }
    }

    private void RunAdbAsync(string arguments)
    {
        Task.Run(() =>
        {
            try
            {
                var devices = GetConnectedDevices();

                if (!string.IsNullOrEmpty(TargetDeviceSerial))
                {
                    RunSingleAdb($"-s {TargetDeviceSerial} {arguments}");
                    return;
                }

                if (devices.Count > 0)
                {
                    foreach (var serial in devices)
                    {
                        RunSingleAdb($"-s {serial} {arguments}");
                    }
                }
                else
                {
                    RunSingleAdb(arguments);
                }
            }
            catch (Exception ex)
            {
                Debug.WriteLine($"[DeviceController] Error executing adb {arguments}: {ex.Message}");
            }
        });
    }
}
