# 个人中心 + 角色权限验证（临时脚本，不入库）
$ErrorActionPreference = 'Continue'
$base = 'http://127.0.0.1:8080/api'
$tmp = Join-Path $env:TEMP 'ptest'
New-Item -ItemType Directory -Force -Path $tmp | Out-Null
$script:pass = 0; $script:fail = 0

function Api {
    param([string]$Method, [string]$Path, [string]$Body, [string]$Token)
    $bf = Join-Path $tmp 'b.json'; $of = Join-Path $tmp 'o.txt'
    $a = @('-s','-o',$of,'-w','%{http_code}','-X',$Method,"$base$Path")
    if ($Token) { $a += @('-H', "Authorization: Bearer $Token") }
    if ($Body) {
        [System.IO.File]::WriteAllText($bf, $Body, (New-Object System.Text.UTF8Encoding($false)))
        $a += @('-H','Content-Type: application/json; charset=utf-8','--data-binary',"@$bf")
    }
    $code = (& curl.exe @a 2>$null | Out-String).Trim()
    $text = if (Test-Path $of) { [System.IO.File]::ReadAllText($of, [System.Text.Encoding]::UTF8) } else { '' }
    $j = $null; if ($text) { try { $j = $text | ConvertFrom-Json } catch {} }
    return @{ Http = [int]$code; Body = $text; Json = $j }
}

function Check {
    param([string]$Name, $R, [int]$WantCode, [int]$WantHttp)
    $code = if ($R.Json) { $R.Json.code } else { $null }
    $ok = ($code -eq $WantCode) -and ($R.Http -eq $WantHttp)
    if ($ok) { $script:pass++ } else { $script:fail++ }
    Write-Host ("[{0}] {1}" -f $(if($ok){'PASS'}else{'FAIL'}), $Name)
    if (-not $ok) { Write-Host ("       HTTP={0}(want {1}) code={2}(want {3}) body={4}" -f $R.Http,$WantHttp,$code,$WantCode,$R.Body) }
}

function Login($u, $p) {
    $r = Api 'POST' '/auth/login' (("{0}{1}{2}{3}{4}" -f '{"username":"',$u,'","password":"',$p,'"}'))
    return $r
}

Write-Host '===== 1. 角色可见性 ====='
$alice = Login 'alice' 'alice123'
$bob = Login 'bob' 'bob123'
Check 'alice 登录且角色为 ADMIN' @{ Http=200; Json=@{ code = if ($alice.Json.data.user.role -eq 'ADMIN') { 0 } else { -1 } } } 0 200
Check 'bob 登录且角色为 USER' @{ Http=200; Json=@{ code = if ($bob.Json.data.user.role -eq 'USER') { 0 } else { -1 } } } 0 200
$at = $alice.Json.data.token; $bt = $bob.Json.data.token

Write-Host ''
Write-Host '===== 2. 管理接口权限边界 ====='
Check 'alice(ADMIN) GET /users -> 0' (Api 'GET' '/users?current=1&size=5' $null $at) 0 200
$r = Api 'GET' '/users?current=1&size=5' $null $bt
Check 'bob(USER) GET /users -> 403/4030' $r 4030 403
Check 'bob(USER) GET /users/1 -> 4030' (Api 'GET' '/users/1' $null $bt) 4030 403
Check 'bob(USER) GET /users/stats -> 4030' (Api 'GET' '/users/stats' $null $bt) 4030 403
Check 'bob(USER) PUT /users/1 改 alice 密码 -> 4030' (Api 'PUT' '/users/1' '{"username":"alice","password":"hacked123"}' $bt) 4030 403
Check 'bob(USER) DELETE /users/1 -> 4030' (Api 'DELETE' '/users/1' $null $bt) 4030 403
Check 'bob(USER) POST /users -> 4030' (Api 'POST' '/users' '{"username":"evil","password":"evil1234"}' $bt) 4030 403
Check 'alice 仍能正常访问（未被上面的尝试影响）' (Api 'GET' '/users/1' $null $at) 0 200

Write-Host ''
Write-Host '===== 3. 个人中心：改自己的资料（bob） ====='
$r = Api 'PUT' '/auth/profile' '{"nickname":"鲍勃","email":"bob@example.com","bio":"我是测试用户","age":32}' $bt
Check 'bob PUT /auth/profile -> 0' $r 0 200
if ($r.Json) {
    Check '返回的 nickname 已生效' @{ Http=200; Json=@{ code = if ($r.Json.data.nickname -eq '鲍勃') { 0 } else { -1 } } } 0 200
    Check '返回的 age 已生效' @{ Http=200; Json=@{ code = if ($r.Json.data.age -eq 32) { 0 } else { -1 } } } 0 200
}
$me = Api 'GET' '/auth/me' $null $bt
Check 'me 读到新 nickname' @{ Http=200; Json=@{ code = if ($me.Json.data.nickname -eq '鲍勃') { 0 } else { -1 } } } 0 200

Write-Host ''
Write-Host '===== 4. 关键：个人资料里塞 role 不能提权 ====='
$r = Api 'PUT' '/auth/profile' '{"nickname":"鲍勃","role":"ADMIN"}' $bt
Check 'bob 请求体塞 role=ADMIN -> 请求成功但忽略' $r 0 200
$me2 = Api 'GET' '/auth/me' $null $bt
Check 'bob 角色仍是 USER（未被提权）' @{ Http=200; Json=@{ code = if ($me2.Json.data.role -eq 'USER') { 0 } else { -1 } } } 0 200
$r2 = Api 'GET' '/users?current=1&size=5' $null $bt
Check 'bob 提权尝试后仍无管理权限' $r2 4030 403

Write-Host ''
Write-Host '===== 5. 改密码 ====='
$r = Api 'PUT' '/auth/password' '{"oldPassword":"wrong-old","newPassword":"bobNew123"}' $bt
Check '原密码错误 -> 4000（不是 4010，避免被登出）' $r 4000 200
Check '原密码错误后令牌仍有效' (Api 'GET' '/auth/me' $null $bt) 0 200
$r = Api 'PUT' '/auth/password' '{"oldPassword":"bob123","newPassword":"12345"}' $bt
Check '新密码过短 -> 4000' $r 4000 200
$r = Api 'PUT' '/auth/password' '{"oldPassword":"bob123","newPassword":"bob123"}' $bt
Check '新旧密码相同 -> 4000' $r 4000 200
$r = Api 'PUT' '/auth/password' '{"oldPassword":"bob123","newPassword":"bobNew123"}' $bt
Check '正确改密 -> 0' $r 0 200
Check '旧密码登录失败 -> 4010' (Login 'bob' 'bob123') 4010 200
Check '新密码登录成功 -> 0' (Login 'bob' 'bobNew123') 0 200

Write-Host ''
Write-Host '===== 6. 管理员自我保护 ====='
Check 'alice 改自己的 role -> 4000' (Api 'PUT' '/users/1' '{"username":"alice","role":"USER"}' $at) 4000 200
Check 'alice 删自己 -> 4000' (Api 'DELETE' '/users/1' $null $at) 4000 200
Check 'alice 角色仍是 ADMIN' @{ Http=200; Json=@{ code = if ((Api 'GET' '/auth/me' $null $at).Json.data.role -eq 'ADMIN') { 0 } else { -1 } } } 0 200

Write-Host ''
Write-Host '===== 7. 非法角色值被拒 ====='
Check 'alice 新增用户 role=hacker -> 4000' (Api 'POST' '/users' '{"username":"rolehack","password":"hack1234","role":"hacker"}' $at) 4000 200
Check 'alice 新增用户 role=USER -> 0' (Api 'POST' '/users' '{"username":"roleok1","password":"ok12345","role":"USER"}' $at) 0 200

Write-Host ''
Write-Host '===== 8. 收尾：把测试改动还原，脚本可重复运行 ====='
# 这个脚本会改 bob 的密码和昵称。不还原的话下次跑会因为「新密码不对」而失败，
# 而且会留下一堆测试数据 —— 验证脚本必须能反复跑。
$r = Api 'PUT' '/users/2' '{"username":"bob","email":"bob@example.com","age":31,"role":"USER","password":"bob123"}' $at
Check '用管理员把 bob 密码/角色还原' $r 0 200
$loginBob = Login 'bob' 'bob123'
Check 'bob 已能用原密码登录' @{ Http=200; Json=@{ code = $loginBob.Json.code } } 0 200
# 清掉昵称/简介（PUT 时不传即置 null）
$null = Api 'PUT' '/users/2' '{"username":"bob","email":"bob@example.com","age":31,"role":"USER"}' $at

Write-Host ''
Write-Host '=================================================='
Write-Host ("汇总: PASS={0}  FAIL={1}" -f $script:pass, $script:fail)
if ($script:fail -gt 0) { exit 1 }
