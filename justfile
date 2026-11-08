# Windows PowerShell, not pwsh: PowerShell 7 from the Microsoft Store runs in an app sandbox that redirects writes to
# %APPDATA% (where Archi keeps dropins and settings) to a private copy, so Archi would never see what we install
set windows-shell := ["powershell.exe", "-NoLogo", "-NoProfile", "-Command"]

gw := if os_family() == "windows" { '.\gradlew.bat' } else { "./gradlew" }

# list recipes
default:
    @just --list --unsorted

# core and plugin tests
test *args:
    {{gw}} test {{args}}

# build the installable .archiplugin into build/dist
plugin:
    {{gw}} :plugin:archiplugin

# install the plugin into the local Archi dropins folder (restart Archi afterwards)
install:
    {{gw}} :plugin:installDev

# everything: tests + plugin
check:
    {{gw}} build
