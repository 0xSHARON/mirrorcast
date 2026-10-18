using System.Configuration;
using System.Data;
using System.IO;
using System.Windows;

namespace MirrorCast.Receiver;

/// <summary>
/// Interaction logic for App.xaml
/// </summary>
public partial class App : Application
{
    protected override void OnStartup(StartupEventArgs e)
    {
        base.OnStartup(e);

        AppDomain.CurrentDomain.UnhandledException += (s, ev) =>
        {
            File.AppendAllText("crash.log", $"[AppDomain] {ev.ExceptionObject}\n");
        };

        DispatcherUnhandledException += (s, ev) =>
        {
            File.AppendAllText("crash.log", $"[Dispatcher] {ev.Exception}\n");
            ev.Handled = false;
        };
    }
}

