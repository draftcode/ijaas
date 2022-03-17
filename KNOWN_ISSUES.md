# Known Issues

* I feel like the LSP version is slower than the non-LSP version.
* Code completion won't trigger reliably.
* I want to see the return types in the code completion popup.
* Error diagnostics won't be updated at the first save action. The second save
  action does update them.

## publishDiagnostics -> codeAciton -> executeCommand problem

I tried to expose IntelliJ's code actions via textDocument/codeAction. Based on
`ProblemDescriptor#quickFix`, it seemed like it's just exposing it via
codeAction and actually executing it at executeCommand.

It turned out that the executeCommand needs to call applyEdit to actually
applying the fix. This means that we need to calculate TextEdit without changing
the actual file. I'm not sure how to do that without triggering the file changes
because the QuickFix interface won't provide a way to do this completely on
memory. LSP doesn't provide a way to change the file on the server side, and
instruct the client to reload the file from the disk (which is understandable).

I checked the subtypes of QuickFix to see if there's a way to get a preview.
It's possible for certain subtypes. But this means that we need to calculate the
TextEdit out of this preview. It's cumbersome.

Considering that I've been using only OrganizeImport (finding out the missing
import statements and remove unused import statements), I feel I'm OK without
having a quickfix. It's nice to have feature, but I have no idea how to
implement it without completely changing IntelliJ's QuickFix model.
