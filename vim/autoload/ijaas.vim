" Copyright 2017 Google Inc.
"
" Licensed under the Apache License, Version 2.0 (the "License");
" you may not use this file except in compliance with the License.
" You may obtain a copy of the License at
"
"     https://www.apache.org/licenses/LICENSE-2.0
"
" Unless required by applicable law or agreed to in writing, software
" distributed under the License is distributed on an "AS IS" BASIS,
" WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
" See the License for the specific language governing permissions and
" limitations under the License.
let s:cpo_save = &cpo
set cpo&vim

if exists("$IJAAS_PORT")
  let s:ch = ch_open('localhost:' . $IJAAS_PORT)
else
  let s:ch = ch_open('localhost:5800')
endif

function! ijaas#call(method, params) abort
  let l:ci = ch_info(s:ch)
  if type(l:ci) != type({}) || l:ci.status != 'open'
    throw 'ijaas: Not connected'
  endif
  let l:response = ch_evalexpr(
        \ s:ch,
        \ {'method': a:method, 'params': a:params},
        \ {'timeout': 3 * 1000})
  if type(l:response) != type({})
    throw 'ijaas: Timeout'
  endif
  if has_key(l:response, 'error') || has_key(l:response, 'cause')
    if has_key(l:response, 'error')
      echo l:response['error']
    endif
    if has_key(l:response, 'cause')
      for l:line in split(l:response['cause'], "\n")
        echom substitute(l:line, "	", '    ', 'g')
      endfor
    endif
    throw 'ijaas: RPC error'
  endif
  return l:response['result']
endfunction

function! ijaas#complete(findstart, base) abort
  let l:col = col('.') - 1
  let l:line = getline('.')
  while l:col > 0 && l:line[l:col-1] =~# '\a'
    let l:col -= 1
  endwhile
  if a:findstart
    return l:col
  endif

  let l:lines = getline(1, '$')
  let l:pos = getcurpos()
  let l:pos[2] = l:col
  if l:pos[1] == 1 " lnum
    let l:text = join(l:lines, "\n")
    let l:offset = l:pos[2] - 1 " col
  else
    " Join the lines from beginning to (the cursor line - 1).
    let l:text = join(l:lines[0:l:pos[1]-2], "\n")
    let l:offset = len(l:text) + 1 + l:pos[2] " \n + col
    " Join the rest of the lines.
    let l:text .= "\n" . join(l:lines[l:pos[1]-1:], "\n")
  endif

  let ret = ijaas#call('java_complete', {
        \ 'file': expand('%:p'),
        \ 'text': l:text,
        \ 'offset': l:offset,
        \ })['completions']
  return filter(ret, 'stridx(v:val["word"], a:base) == 0')
endfunction

function! ijaas#buf_write_post() abort
  let l:result = ijaas#call('java_src_update', {'file': expand('%:p')})

  if !has_key(l:result, 'problems') || len(l:result['problems']) == 0
    call ijaas#set_problems([])
  else
    call ijaas#set_problems(l:result['problems'])
  end
endfunction

sign define IjaasErrorSign text=>> texthl=Error
sign define IjaasWarningSign text=>> texthl=Todo

function! ijaas#set_problems(problems) abort
  let l:filename = expand('%:p')
  sign unplace *
  let l:id = 1
  for l:problem in a:problems
    let l:problem['filename'] = l:filename
    if l:problem['type'] ==# 'E'
      exec 'sign place ' . l:problem['lnum'] . ' line=' . l:problem['lnum'] . ' name=IjaasErrorSign file=' . l:filename
    elseif l:problem['type'] ==# 'W'
      exec 'sign place ' . l:problem['lnum'] . ' line=' . l:problem['lnum'] . ' name=IjaasWarningSign file=' . l:filename
    endif
    let l:id += 1
  endfor
  call setqflist(a:problems)
  cwindow
endfunction

function! ijaas#organize_import() abort
  let l:response = ijaas#call('java_get_import_candidates', {
        \ 'file': expand('%:p'),
        \ 'text': join(getline(1, '$'), "\n"),
        \ })

  let l:choices = l:response['choices']
  if empty(l:choices)
    return
  endif
  if exists('*fzf#run')
    let l:state = { 'question': l:choices }
    call s:select_imports_fzf(l:state, "")
  else
    for l:items in l:choices
      let l:sel = s:select_imports_normal(l:items)
      if l:sel == ""
        " Abort organize import
        return
      end
      call s:add_import(l:sel)
    endfor
  endif
endfunction

function! s:select_imports_fzf(state, selected) abort
  if a:selected != ""
    call s:add_import(a:selected)
  endif
  while !empty(a:state['question'])
    let l:question = a:state['question'][0]
    let a:state['question'] = a:state['question'][1:]
    if len(l:question) == 1
      call s:add_import(l:question[0])
    else
      call fzf#run({
            \ 'source': l:question,
            \ 'down': '40%',
            \ 'sink': function('s:select_imports_fzf', [a:state]),
            \ })
      return
    endif
  endwhile
endfunction

function! s:select_imports_normal(inputs) abort
  if len(a:inputs) == 1
    return a:inputs[0]
  endif
  let l:text = ""
  let l:index = 1
  for l:item in a:inputs
    let l:text .= l:index . ") " . l:item . "\n"
    let l:index += 1
  endfor

  let l:selected = input(l:text . "> ")
  if l:selected =~# '^\d\+$'
    return a:inputs[str2nr(l:selected)-1]
  else
    return ""
  endif
endfunction

function! s:add_import(input) abort
  let l:lnum = 1
  if a:input =~# '^import static '
    let l:last_static_import = 0
    let l:first_import = 0

    while l:lnum <= line('$')
      let l:line = getline(l:lnum)

      if l:line =~# '^import static '
        if a:input <# l:line
          call append(l:lnum-1, a:input)
          return
        endif
        let l:last_static_import = l:lnum
      elseif l:line =~# '^import '
        let l:first_import = l:lnum
        break
      endif
      let l:lnum += 1
    endwhile

    if l:last_static_import != 0
      call append(l:last_static_import, a:input)
      return
    elseif l:first_import != 0
      call append(l:first_import-1, [a:input, ''])
      return
    endif
  else
    let l:last_import = 0

    while l:lnum <= line('$')
      let l:line = getline(l:lnum)

      if l:line =~# '^import static '
        " Ignore
      elseif l:line =~# '^import '
        if a:input <# l:line
          call append(l:lnum-1, a:input)
          return
        endif
        let l:last_import = l:lnum
      endif
      let l:lnum += 1
    endwhile

    if l:last_import != 0
      call append(l:last_import, a:input)
      return
    endif
  endif

  let l:lnum = 1
  while l:lnum <= line('$')
    let l:line = getline(l:lnum)
    if l:line =~# '^package '
      call append(l:lnum, ['', a:input])
      return
    endif
    let l:lnum += 1
  endwhile
endfunction

let &cpo = s:cpo_save
unlet s:cpo_save
