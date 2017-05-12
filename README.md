# IntelliJ as a Service

Make IntelliJ as a Java server that does autocompletion for Vim.

This is not an official Google product (i.e. a 20% project).

## Installation

1. git clone.
2. Import project into IntelliJ. Use Gradle plugin.
3. Run `gradle buildPlugin`. It creates `build/distributions/ijaas-*.zip` at the
   git root dir.
4. Select "File" menu and click "Settings...". In "Plugins" menu, click "Install
   plugin from disk..." button. Choose `ijaas-*.zip`. You can uninstall this
   plugin from this menu.
5. Restart IntelliJ.
6. Add "vim" directory to your runtimepath in Vim in your own way.
   (e.g. Plug "$HOME/src/ijaas/vim").

## Development

If you want to isolate your development version and the current version, you
might need two clones. You can load Vim plugins conditionally by using
environment variables.

```
if !exists('$USE_DEV_IJAAS')
  Plug '$HOME/src/ijaas-dev/vim'
else
  Plug '$HOME/src/ijaas/vim'
endif
```

You can start another IntelliJ instance by using `gradle runIdea`. You can pass
`-Dijaas.port=5801` to make the testing IntelliJ process listen on a different
port (see https://github.com/JetBrains/gradle-intellij-plugin/issues/18).
Connect to the testing IntelliJ with `USE_DEV_IJAAS=1 IJAAS_PORT=5801 vim`. The
ijaas vim plugin will recognize `IJAAS_PORT` and use that to connect to the
ijaas IntelliJ plugin.
