# IntelliJ as a Service

Make IntelliJ as a Java LSP server that does autocompletion for Vim.

## Installation

1. git clone.
2. Import project into IntelliJ. Use Gradle plugin.
3. Run `gradle buildPlugin`. It creates `build/distributions/ijaas-*.zip` at the
   git root dir.
4. Select "File" menu and click "Settings...". In "Plugins" menu, click "Install
   plugin from disk..." button. Choose `ijaas-*.zip`. You can uninstall this
   plugin from this menu.
5. Restart IntelliJ.

## History

This was started as a 20% project at Google when draftcode was working there.
That's why some files are copyrighted by Google. Now he left the company, and
the repository was moved to his personal account.

The initial implementation was written using Vim's channel feature. Then, this
is rewritten to support Language Server Protocol. This version doesn't have a
feature parity, and hence WIP.
