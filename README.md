# IntelliJ as a Service

Make IntelliJ as a Java server that does autocompletion for Vim.

This is not an official Google product (i.e. a 20% project).

## Installation

1. git clone.
2. Import project into IntelliJ. Use Gradle plugin.
3. Run `gradle buildPlugin`. It creates `build/distributions/ijaas-*.zip` at the
   git root dir. (You can pass `-Pintellij.version=IC-2017.2.6` to specify the
   IntelliJ version.)
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

## Using with ALE

You can define an ALE linter.

```
# Disable buf_write_post. Files are checked by ALE.
let g:ijaas_disable_buf_write_post = 1

# Define ijaas linter.
function! s:ijaas_handle(buffer, lines) abort
  let l:response = json_decode(join(a:lines, '\n'))[1]
  if has_key(l:response, 'error') || has_key(l:response, 'cause')
    return [{
          \  'lnum': 1,
          \  'text': 'ijaas: RPC error: error=' . l:response['error']
          \    . ' cause=' . l:response['cause'],
          \}]
  endif

  return l:response['result']['problems']
endfunction
call ale#linter#Define('java', {
      \   'name': 'ijaas',
      \   'executable': 'nc',
      \   'command': "echo '[0, {\"method\": \"java_src_update\", \"params\": {\"file\": \"%s\"}}]' | nc localhost 5800 -N",
      \   'lint_file': 1,
      \   'callback': function('s:ijaas_handle'),
      \ })
```
