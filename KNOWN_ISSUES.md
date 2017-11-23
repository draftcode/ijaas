# Known Issues

* Autocomplete omits some results when there are many candidates.
* OrganizeImport adds checkerframework's strange `m` class.
* OrganizeImport won't add any static import.
* OrganizeImport somehow can add the same imports.
* Everything is slow.
* The timeout doesn't work well. This is probably because CodeSmellDetector and
  inspection tools internally switch to the swing thread and ProgressIndicator
  is not chained properly.
* Detected problems are duplicated.
* There might be a resource leak in the IntelliJ side.
* (Maybe this is not this plugin's issue, but) after BufWritePost, sometimes Vim
  goes into a strange state that it accepts ex commands only. No redraw.
