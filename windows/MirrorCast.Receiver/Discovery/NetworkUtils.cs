using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;

namespace MirrorCast.Receiver.Discovery;

public static class NetworkUtils
{
    public static List<string> GetLocalIPv4Addresses()
    {
        var addresses = new List<string>();
        try
        {
            var interfaces = NetworkInterface.GetAllNetworkInterfaces()
                .Where(ni => ni.OperationalStatus == OperationalStatus.Up &&
                             ni.NetworkInterfaceType != NetworkInterfaceType.Loopback)
                .OrderByDescending(ni =>
                {
                    int score = 0;
                    if (ni.NetworkInterfaceType == NetworkInterfaceType.Wireless80211) score += 10;
                    else if (ni.NetworkInterfaceType == NetworkInterfaceType.Ethernet) score += 5;

                    try
                    {
                        var props = ni.GetIPProperties();
                        if (props.GatewayAddresses.Any(g => g.Address != null && !IPAddress.IsLoopback(g.Address)))
                        {
                            score += 20; // Has active default gateway (connected to real router/Wi-Fi)
                        }
                    }
                    catch { }
                    return score;
                });

            foreach (var ni in interfaces)
            {
                var ipProps = ni.GetIPProperties();
                foreach (var addr in ipProps.UnicastAddresses)
                {
                    if (addr.Address.AddressFamily == AddressFamily.InterNetwork &&
                        !IPAddress.IsLoopback(addr.Address))
                    {
                        var ipStr = addr.Address.ToString();
                        // Ignore link-local APIPA addresses (169.254.x.x)
                        if (!ipStr.StartsWith("169.254."))
                        {
                            addresses.Add(ipStr);
                        }
                    }
                }
            }
        }
        catch
        {
            // Fallback to DNS host entry
            try
            {
                var host = Dns.GetHostEntry(Dns.GetHostName());
                foreach (var ip in host.AddressList)
                {
                    if (ip.AddressFamily == AddressFamily.InterNetwork && !IPAddress.IsLoopback(ip))
                    {
                        var ipStr = ip.ToString();
                        if (!ipStr.StartsWith("169.254."))
                        {
                            addresses.Add(ipStr);
                        }
                    }
                }
            }
            catch { }
        }

        return addresses.Distinct().ToList();
    }
}
