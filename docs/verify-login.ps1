# 登录接口验证脚本（PowerShell 5.1 兼容）
#
# 为什么用 curl.exe 而不是 Invoke-WebRequest：
#   1) Invoke-RestMethod 在 PS 5.1 下会把不带 charset 的 application/json 按 Latin-1 解码，
#      中文变成乱码，断言会误判；
#   2) 实测 Invoke-WebRequest 抛异常后，$_.Exception.Response.GetResponseStream() 读出来是空串，
#      拿不到 401 的响应体，而 401 + code=4010 正是本任务要验的核心行为。
#   curl.exe 两者都没问题（Windows 10+ 自带）。
#
# 本文件必须以 **带 BOM 的 UTF-8** 保存：PS 5.1 会把无 BOM 的 UTF-8 当 GBK 读，
# 中文注释会变乱码并引发莫名的语法错误（AGENTS.md 第 10.2 条）。
$ErrorActionPreference = 'Continue'
$base = 'http://127.0.0.1:8080/api'
$script:pass = 0
$script:fail = 0
$tmp = Join-Path $env:TEMP 'myapp-verify'
New-Item -ItemType Directory -Force -Path $tmp | Out-Null

# 调一次接口，返回 @{ Status; Body; Json }
function Invoke-Curl {
    param([string]$Method, [string]$Path, [string]$Body, [string]$Token)
    $bodyFile = Join-Path $tmp 'body.json'
    $outFile = Join-Path $tmp 'out.txt'
    $args = @('-s', '-o', $outFile, '-w', '%{http_code}', '-X', $Method, "$base$Path")
    if ($Token) { $args += @('-H', "Authorization: Bearer $Token") }
    if ($Body) {
        # 用文件传 body，避免 PowerShell 把 JSON 里的引号吃掉
        [System.IO.File]::WriteAllText($bodyFile, $Body, (New-Object System.Text.UTF8Encoding($false)))
        $args += @('-H', 'Content-Type: application/json; charset=utf-8', '--data-binary', "@$bodyFile")
    }
    $status = (& curl.exe @args 2>$null | Out-String).Trim()
    $text = if (Test-Path $outFile) { [System.IO.File]::ReadAllText($outFile, [System.Text.Encoding]::UTF8) } else { '' }
    $parsed = $null
    if ($text) { try { $parsed = $text | ConvertFrom-Json } catch { } }
    return @{ Status = [int]$status; Body = $text; Json = $parsed }
}

function Assert {
    param([string]$Name, $Result, [int]$WantCode, [int]$WantHttp)
    $code = if ($Result.Json) { $Result.Json.code } else { $null }
    $ok = ($code -eq $WantCode) -and ($Result.Status -eq $WantHttp)
    if ($ok) { $script:pass++ } else { $script:fail++ }
    $mark = if ($ok) { 'PASS' } else { 'FAIL' }
    Write-Host ("[{0}] {1}" -f $mark, $Name)
    if (-not $ok) {
        Write-Host ("       HTTP={0}(期望{1}) code={2}(期望{3}) body={4}" -f $Result.Status, $WantHttp, $code, $WantCode, $Result.Body)
    } elseif ($Result.Json -and $Result.Json.message -and $Result.Json.message -ne 'ok') {
        Write-Host ("       code={0} message={1}" -f $code, $Result.Json.message)
    }
    return $ok
}

Write-Host '===== 1. 健康检查（白名单） ====='
Assert 'GET /api/health 免登录可访问' (Invoke-Curl 'GET' '/health') 0 200 | Out-Null

Write-Host ''
Write-Host '===== 2. 登录成功并返回真 JWT ====='
$login = Invoke-Curl 'POST' '/auth/login' '{"username":"alice","password":"alice123"}'
$loginOk = Assert 'POST /api/auth/login (alice/alice123)' $login 0 200
$token = $null
if ($loginOk) {
    $token = $login.Json.data.token
    Write-Host ("       tokenType={0} expiresIn={1}" -f $login.Json.data.tokenType, $login.Json.data.expiresIn)
    $parts = $token.Split('.')
    Write-Host ("       JWT 段数={0}（应为 3）" -f $parts.Count)
    foreach ($i in 1, 0) {
        $seg = $parts[$i].Replace('-', '+').Replace('_', '/')
        switch ($seg.Length % 4) { 2 { $seg += '==' } 3 { $seg += '=' } }
        $decoded = [System.Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($seg))
        $label = if ($i -eq 1) { 'payload' } else { 'header ' }
        Write-Host ("       {0}={1}" -f $label, $decoded)
    }
}

Write-Host ''
Write-Host '===== 3. 失败分支文案一致（防用户名枚举） ====='
$badPwd = Invoke-Curl 'POST' '/auth/login' '{"username":"alice","password":"wrong-password"}'
$noUser = Invoke-Curl 'POST' '/auth/login' '{"username":"no-such-user","password":"whatever"}'
Assert '密码错误 -> 4010' $badPwd 4010 200 | Out-Null
Assert '用户不存在 -> 4010' $noUser 4010 200 | Out-Null
if ($badPwd.Json.message -eq $noUser.Json.message) {
    $script:pass++; Write-Host '[PASS] 两种失败 message 完全一致'
} else {
    $script:fail++; Write-Host ("[FAIL] 文案不一致: '{0}' vs '{1}'" -f $badPwd.Json.message, $noUser.Json.message)
}

Write-Host ''
Write-Host '===== 4. 参数校验 ====='
Assert '空用户名密码 -> 4000' (Invoke-Curl 'POST' '/auth/login' '{"username":"","password":""}') 4000 200 | Out-Null

Write-Host ''
Write-Host '===== 5. 未带令牌访问受保护接口（HTTP 401 + code 4010） ====='
Assert 'GET /api/users 无令牌' (Invoke-Curl 'GET' '/users') 4010 401 | Out-Null
Assert 'GET /api/auth/me 无令牌' (Invoke-Curl 'GET' '/auth/me') 4010 401 | Out-Null

Write-Host ''
Write-Host '===== 6. 畸形与签名伪造的令牌 ====='
Assert 'GET /api/users 畸形令牌' (Invoke-Curl 'GET' '/users' $null 'not-a-real-jwt') 4010 401 | Out-Null
$forged = 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhbGljZSIsInVpZCI6IjEiLCJleHAiOjk5OTk5OTk5OTl9.aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'
Assert 'GET /api/users 伪造签名' (Invoke-Curl 'GET' '/users' $null $forged) 4010 401 | Out-Null

# 用过期逻辑再验一次：篡改 payload 但保留原签名，签名必须校验失败
if ($token) {
    $seg = $token.Split('.')
    Assert 'GET /api/users 篡改 payload 后签名失效' (Invoke-Curl 'GET' '/users' $null ($seg[0] + '.' + $seg[1] + 'x.' + $seg[2])) 4010 401 | Out-Null
}

Write-Host ''
Write-Host '===== 7. 带真令牌访问受保护接口 ====='
if ($token) {
    $me = Invoke-Curl 'GET' '/auth/me' $null $token
    Assert 'GET /api/auth/me 带令牌' $me 0 200 | Out-Null
    Write-Host ("       me={0}" -f $me.Body)
    $users = Invoke-Curl 'GET' '/users?current=1&size=10' $null $token
    Assert 'GET /api/users 带令牌' $users 0 200 | Out-Null
    Write-Host ("       total={0}" -f $users.Json.data.total)

    Write-Host ''
    Write-Host '===== 8. 密码哈希绝不出现在响应里 ====='
    foreach ($probe in @(@{ n = '/api/users 全量'; p = '/users?current=1&size=100' }, @{ n = '/api/auth/me'; p = '/auth/me' })) {
        $body = (Invoke-Curl 'GET' $probe.p $null $token).Body
        if ($body -match 'passwordHash' -or $body -match '\$2a\$' -or $body -match '\$2b\$') {
            $script:fail++; Write-Host ("[FAIL] {0} 泄漏了密码哈希: {1}" -f $probe.n, $body)
        } else {
            $script:pass++; Write-Host ("[PASS] {0} 不含密码哈希" -f $probe.n)
        }
    }

    Write-Host ''
    Write-Host '===== 9. 新增用户必须带密码 ====='
    Assert 'POST /api/users 不带密码 -> 4000' (Invoke-Curl 'POST' '/users' '{"username":"no-password-user","email":"x@example.com"}' $token) 4000 200 | Out-Null
    Assert 'POST /api/users 密码过短 -> 4000' (Invoke-Curl 'POST' '/users' '{"username":"shortpwd","password":"123"}' $token) 4000 200 | Out-Null

    Write-Host ''
    Write-Host '===== 10. 端到端：新建用户 -> 用它登录 ====='
    $newName = 'dave' + (Get-Random -Maximum 99999)
    Assert 'POST /api/users 建号' (Invoke-Curl 'POST' '/users' (("{0}{1}{2}" -f '{"username":"', $newName, '","password":"dave123","email":"dave@example.com","age":40}')) $token) 0 200 | Out-Null
    Assert '新用户用正确密码登录' (Invoke-Curl 'POST' '/auth/login' (("{0}{1}{2}" -f '{"username":"', $newName, '","password":"dave123"}')) ) 0 200 | Out-Null
    Assert '新用户用错误密码登录' (Invoke-Curl 'POST' '/auth/login' (("{0}{1}{2}" -f '{"username":"', $newName, '","password":"nope"}')) ) 4010 200 | Out-Null

    Write-Host ''
    Write-Host '===== 11. 改密码后旧密码失效 ====='
    $uid = $null
    $page = Invoke-Curl 'GET' '/users?current=1&size=100' $null $token
    foreach ($rec in $page.Json.data.records) { if ($rec.username -eq $newName) { $uid = $rec.id } }
    if ($uid) {
        Assert 'PUT /api/users/{id} 改密码' (Invoke-Curl 'PUT' "/users/$uid" (("{0}{1}{2}" -f '{"username":"', $newName, '","password":"newpass456"}')) $token) 0 200 | Out-Null
        Assert '旧密码已失效' (Invoke-Curl 'POST' '/auth/login' (("{0}{1}{2}" -f '{"username":"', $newName, '","password":"dave123"}')) ) 4010 200 | Out-Null
        Assert '新密码可登录' (Invoke-Curl 'POST' '/auth/login' (("{0}{1}{2}" -f '{"username":"', $newName, '","password":"newpass456"}')) ) 0 200 | Out-Null
        Assert 'PUT 不带密码则密码不变' (Invoke-Curl 'PUT' "/users/$uid" (("{0}{1}{2}" -f '{"username":"', $newName, '","age":41}')) $token) 0 200 | Out-Null
        Assert '不带密码修改后仍可用原密码登录' (Invoke-Curl 'POST' '/auth/login' (("{0}{1}{2}" -f '{"username":"', $newName, '","password":"newpass456"}')) ) 0 200 | Out-Null
    } else {
        $script:fail++; Write-Host '[FAIL] 未找到刚创建的用户，跳过第 11 组'
    }
} else {
    $script:fail++; Write-Host '[FAIL] 未取得令牌，第 7-11 组无法验证'
}

Write-Host ''
Write-Host '===== 12. 注册接口 ====='
$reg = 'reg' + (Get-Random -Maximum 999999)
$regOk = Invoke-Curl 'POST' '/auth/register' (("{0}{1}{2}" -f '{"username":"', $reg, '","password":"secret123","email":"reg@example.com"}'))
Assert 'POST /api/auth/register 正常注册 -> 0' $regOk 0 200 | Out-Null
if ($regOk.Json.data.token) {
    # 注册即登录：拿注册返回的令牌直接访问受保护接口
    Assert '注册返回的令牌可直接访问 /api/auth/me' (Invoke-Curl 'GET' '/auth/me' $null $regOk.Json.data.token) 0 200 | Out-Null
} else {
    $script:fail++; Write-Host '[FAIL] 注册响应没有 token（注册即登录失效）'
}
Assert '注册后可登录' (Invoke-Curl 'POST' '/auth/login' (("{0}{1}{2}" -f '{"username":"', $reg, '","password":"secret123"}')) ) 0 200 | Out-Null

Write-Host ''
Write-Host '----- 12b. 重名与校验 -----'
Assert '重名注册 -> 4009' (Invoke-Curl 'POST' '/auth/register' (("{0}{1}{2}" -f '{"username":"', $reg, '","password":"secret123"}')) ) 4009 200 | Out-Null
Assert '与种子用户 alice 重名 -> 4009' (Invoke-Curl 'POST' '/auth/register' '{"username":"alice","password":"secret123"}') 4009 200 | Out-Null
Assert '密码 5 位 -> 4000' (Invoke-Curl 'POST' '/auth/register' '{"username":"regshort1","password":"12345"}') 4000 200 | Out-Null
Assert '用户名空 -> 4000' (Invoke-Curl 'POST' '/auth/register' '{"username":"","password":"secret123"}') 4000 200 | Out-Null
Assert '用户名纯空格 -> 4000' (Invoke-Curl 'POST' '/auth/register' '{"username":"   ","password":"secret123"}') 4000 200 | Out-Null
Assert '邮箱格式错 -> 4000' (Invoke-Curl 'POST' '/auth/register' '{"username":"regmail1","password":"secret123","email":"not-an-email"}') 4000 200 | Out-Null
Assert '邮箱省略也可注册 -> 0' (Invoke-Curl 'POST' '/auth/register' ("{0}{1}{2}" -f '{"username":"noemail', (Get-Random -Maximum 99999), '","password":"secret123"}')) 0 200 | Out-Null
Assert '密码超 72 字符 -> 4000' (Invoke-Curl 'POST' '/auth/register' (("{0}{1}{2}" -f '{"username":"reglong1","password":"', ('a' * 73), '"}')) ) 4000 200 | Out-Null

Write-Host ''
Write-Host '----- 12c. 关键边界：逻辑删除的用户名仍占唯一索引 -----'
Write-Host '         （MyBatis-Plus 查询会自动过滤 deleted=1，所以「先查重」必然漏判；'
Write-Host '           必须靠唯一键兜底，否则这里会返回 5000 而不是 4009）'
$delUser = 'del' + (Get-Random -Maximum 99999)
$delOk = Invoke-Curl 'POST' '/auth/register' (("{0}{1}{2}" -f '{"username":"', $delUser, '","password":"secret123"}'))
if ($delOk.Json.code -eq 0) {
    $delId = $delOk.Json.data.user.id
    Assert '逻辑删除该用户（走正常删除接口）' (Invoke-Curl 'DELETE' "/users/$delId" $null $token) 0 200 | Out-Null
    Assert '同名再注册 -> 必须是 4009（若为 5000 说明兜底失效）' (Invoke-Curl 'POST' '/auth/register' (("{0}{1}{2}" -f '{"username":"', $delUser, '","password":"secret123"}')) ) 4009 200 | Out-Null
} else {
    $script:fail++; Write-Host "[FAIL] 无法创建用于删除测试的用户: $($delOk.Body)"
}

Write-Host ''
Write-Host '===== 13. 注册接口不得泄漏密码哈希 ====='
$regBody = $regOk.Body
if ($regBody -match 'passwordHash' -or $regBody -match '\$2a\$') {
    $script:fail++; Write-Host "[FAIL] 注册响应泄漏密码哈希: $regBody"
} else {
    $script:pass++; Write-Host '[PASS] 注册响应不含 passwordHash'
}

Write-Host ''
Write-Host '=================================================='
Write-Host ("汇总: PASS={0}  FAIL={1}" -f $script:pass, $script:fail)
if ($script:fail -gt 0) { exit 1 }
