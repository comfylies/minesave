// =====================================================================
// minesave 的 Jenkins 流水线
//
// 它跑在【多分支流水线】里，分支源是 https://github.com/comfylies/minesave.git
// 所以：这个文件就是构建配置本身。提交到哪个分支，就用哪个分支的这份脚本跑。
// =====================================================================

// 参数取值的统一入口，两个坑一次性解决：
//   ① 首次引入某个参数的那次构建，params.X 是 null
//      （参数定义是这次运行过程中才注册到任务上的，下次才有得选）。
//   ② 不能图省事写 params.X ?: true —— Groovy 里 false ?: true 的结果是 true，
//      用户取消勾选反而会被当成勾上。
def pbool(String name, boolean dflt) {
    def v = params[name]
    return (v == null) ? dflt : (v instanceof Boolean ? v : v.toString().toBoolean())
}

pipeline {

    agent any

    options {
        buildDiscarder(logRotator(numToKeepStr: '20', daysToKeepStr: '30'))
        disableConcurrentBuilds()            // 工作区是共用的，同时跑两次会互相踩
        timeout(time: 30, unit: 'MINUTES')   // 兜底：卡死了自动判 ABORTED，不会永远占着执行器
    }

    parameters {
        booleanParam(name: 'SKIP_TESTS',    defaultValue: true,  description: '跳过单元测试。本机没起 MySQL/Meilisearch 时必须勾上')
        booleanParam(name: 'CLEAN_FIRST',   defaultValue: true,  description: '先 mvn clean 从零编译。取消勾选走增量，快很多')
        booleanParam(name: 'SKIP_FRONTEND', defaultValue: false, description: '跳过"前端构建"这条并行分支（npm ci / test / vite build）')
        string(name: 'BUILD_NOTE',          defaultValue: '',     description: '随手写一句备注，会打印到日志里，方便事后区分')
        booleanParam(name: 'CONFIRM_DEPLOY', defaultValue: false, description: '勾上后流水线会停下来等人点「确认发布」（人工卡点 input）')
        booleanParam(name: 'DEPLOY_DRY_RUN', defaultValue: true,  description: '部署演练：连服务器、上传、校验完整性，但不替换不重启。第一次务必保持勾选')
    }

    // triggers 这里【故意留空】。
    // 多分支任务里"盯仓库"由父项目统一负责（父项目 → 触发器 → Periodically if not otherwise run）。
    // 子任务再各自 pollSCM 属于重复劳动，两边同时发现同一个提交还可能抢出重复构建。

    environment {
        // Jenkins 不读 ~/.bashrc —— .bashrc 里配的 JAVA_HOME / PATH 在这里一律无效，必须显式写。
        // minesave 编译目标是 Java 17，这里钉死 17；本机默认 java 是 25，不钉就会不一致。
        JAVA_HOME = '/usr/lib/jvm/java-17-openjdk-amd64'
        PATH = "/home/yf/opt/maven/bin:/usr/lib/jvm/java-17-openjdk-amd64/bin:${env.PATH}"
    }

    stages {

        // ---- 1. 环境侦察：一次把"我是谁、在哪、工具什么版本"打全 ----
        // 这里【没有 checkout scm】：Declarative 已经在最前面自动 checkout 过了
        // （日志里那个 "Declarative: Checkout SCM" 阶段），再调一次是白跑一趟网络。
        stage('环境侦察') {
            steps {
                sh '''
                    echo "工作区  : $WORKSPACE"
                    echo "分支    : [$BRANCH_NAME] / [${GIT_LOCAL_BRANCH}] / [$GIT_BRANCH]"
                    echo "本次提交: $(git rev-parse --short HEAD)  $(git log --oneline -1)"
                    echo "提交总数: $(git rev-list --count HEAD)"
                    echo -n "java    : "; java -version 2>&1 | head -1
                    echo -n "mvn     : "; mvn -version 2>&1 | head -1
                    echo -n "node    : "; node -v
                '''
            }
        }

        // ---- 2. 本次变更：算出"这次动了哪些目录"，并摊开给日志看 ----
        //
        // 这一步把判断结果提前算成两个普通字符串塞进 env，后面的 when 只做字符串比较。
        //
        // ⚠ 为什么不直接在阶段里写 when { changeset pattern: 'Backend/**' } ——
        // changeset 这个 when 条件的实现（ChangeSetConditionalScript）会把 changeSets
        // 对象一直挂在 CPS 调用栈上，而它【不是可序列化的】。跑在 parallel 里的时候，
        // 只要另一条线正好卡在 sh 上（CPS 此时要保存程序快照），整个构建就会这样收场：
        //     java.io.NotSerializableException: hudson.plugins.git.GitChangeSetList
        // 它是随机的：同一份脚本跑了 17 次都好好的，第 18 次才炸。所以老实点绕开它。
        //
        // 顺带修一个原来就有的死条件：changeset 的 pattern 是 Ant 风格，不带 ** 时
        // 只匹配仓库【根目录】。pom.xml / package.json 这两个文件其实都在子目录里
        // （Backend/GameSaves/pom.xml、Frontend/GameSaves_Fronted/package.json），
        // 所以那两条 pattern 从来没生效过。这里用 endsWith 写成真正想要的意思。
        stage('本次变更') {
            steps {
                script {
                    def paths = []
                    currentBuild.changeSets.each { cs ->
                        cs.items.each { item ->
                            item.affectedFiles.each { f -> paths << f.path }
                        }
                    }
                    def empty = paths.isEmpty()
                    // 变更集为空（第一次构建 / 手动 Build Now）→ 两个标记都算 true，
                    // 也就是"全都跑一遍"。否则 changeSets 一空，所有阶段会一起变灰，
                    // 流水线"绿着但什么都没干"。
                    env.CHANGES_EMPTY    = empty ? 'true' : 'false'
                    env.CHANGED_BACKEND  = (empty || paths.any { it.endsWith('pom.xml')    || it.startsWith('Backend/')  }) ? 'true' : 'false'
                    env.CHANGED_FRONTEND = (empty || paths.any { it.endsWith('package.json') || it.startsWith('Frontend/') }) ? 'true' : 'false'

                    echo "Jenkins 记录的变更集数量: ${currentBuild.changeSets.size()}"
                    echo "本次变更文件: ${paths}"
                    echo "变更集为空=${env.CHANGES_EMPTY}  后端相关=${env.CHANGED_BACKEND}  前端相关=${env.CHANGED_FRONTEND}"
                    if (empty) {
                        echo "→ 变更集为空：两个标记都按 true 处理，全都跑一遍"
                    }
                }
                sh '''
                    echo "本次提交 : $GIT_COMMIT"
                    echo "上次成功 : ${GIT_PREVIOUS_SUCCESSFUL_COMMIT:-（无，这是第一次）}"
                    if [ -n "$GIT_PREVIOUS_SUCCESSFUL_COMMIT" ]; then
                        echo "变更文件："
                        git diff --name-only "$GIT_PREVIOUS_SUCCESSFUL_COMMIT" "$GIT_COMMIT" | sed 's/^/    /'
                    else
                        echo "变更文件：（首次构建，没有可比对的基线）"
                    fi
                '''
            }
        }

        // =================================================================
        // 3. 并行区：三条线同时开跑
        //
        // parallel 的语义，最容易被误解的一点：
        //   · 整个 Pipeline 只占【1 个执行器】。分支之间是同一个构建内部的
        //     并发线程，共用同一个工作区，不会各自再占一个执行器。
        //   · 正因为共用工作区，分支之间不能写同一个文件 —— 这里三条线各写各的目录。
        //   · failFast true：任意一条线失败，立刻掐掉还在跑的其它线。
        // =================================================================
        stage('并行构建') {
            failFast true

            parallel {

                // ---- 线 1：后端打包 ----
                stage('后端打包') {
                    // 判断依据是上面『本次变更』算好的标记。
                    // env 里的值永远是字符串，能安全地穿过 CPS 的程序快照 —— 别在这里
                    // 直接碰 currentBuild.changeSets，原因见那个阶段的注释。
                    when {
                        expression { return env.CHANGED_BACKEND == 'true' }
                    }
                    steps {
                        script {
                            // 给后面『归档产物』留标记：这次后端到底编没编。
                            // 不能靠"文件在不在"判断 —— 工作区跨构建持久，会归档到上一次的旧产物。
                            env.BACKEND_RAN = 'true'

                            def skipTests  = pbool('SKIP_TESTS',  true)
                            def cleanFirst = pbool('CLEAN_FIRST', true)

                            // 这就是 Pipeline 比 Freestyle 强的地方：真的是在写程序。
                            def cmd = 'mvn -B ' + (cleanFirst ? 'clean package' : 'package')
                            if (skipTests) {
                                cmd += ' -Dmaven.test.skip=true'
                            }

                            echo "本次参数: SKIP_TESTS=${skipTests}   CLEAN_FIRST=${cleanFirst}"
                            echo "构建备注: ${params.BUILD_NOTE ?: '（未填写）'}"
                            echo "执行命令: ${cmd}"

                            sh 'echo "[后端] 起跑 $(date +%T)"'
                            dir('Backend/GameSaves') {
                                sh cmd
                            }
                            sh 'echo "[后端] 冲线 $(date +%T)"'
                        }
                    }
                    // 阶段级 post：写法一样，作用范围只有这一个 stage。
                    post {
                        always {
                            echo "[后端] 阶段级 post/always —— 这个阶段无论成败都会走到"
                        }
                    }
                }

                // ---- 线 2：前端构建 ----
                stage('前端构建') {
                    // 两个条件都要满足，所以用 allOf 套起来：
                    //   ① 参数没让跳过   ② 这次改的东西跟前端有关
                    when {
                        allOf {
                            expression {
                                def skip = pbool('SKIP_FRONTEND', false)
                                echo "[前端] when 求值：参数原值=${params.SKIP_FRONTEND}（null 按 false 处理）→ skip=${skip}"
                                return !skip
                            }
                            expression { return env.CHANGED_FRONTEND == 'true' }
                        }
                    }
                    steps {
                        script {
                            env.FRONTEND_RAN = 'true'
                        }
                        sh 'echo "[前端] 起跑 $(date +%T)"'
                        dir('Frontend/GameSaves_Fronted') {
                            sh '''
                                # 装依赖。这一步比后端编译还慢，所以做了个缓存判断。
                                # 工作区跨构建持久，node_modules 装一次就一直在；而 npm ci 的
                                # 行为是"把 node_modules 整个删掉重装"，很浪费。
                                # npm ci 会写一个 node_modules/.package-lock.json，
                                # 拿它跟 package-lock.json 比时间戳就够了。
                                if [ -d node_modules ] && [ node_modules/.package-lock.json -nt package-lock.json ]; then
                                    echo "package-lock.json 没变，跳过 npm ci"
                                else
                                    echo "依赖有变化（或第一次），执行 npm ci"
                                    # --include=dev 不能省：环境里若有 NODE_ENV=production，
                                    # npm 会默认不装 devDependencies，vite 就没了，
                                    # 构建会以 "vite: not found" 收场 —— 这个坑踩过。
                                    npm ci --include=dev --no-audit --no-fund
                                fi
                                npm test
                                npm run build
                            '''
                        }
                        sh 'echo "[前端] 冲线 $(date +%T)"'
                    }
                }

                // ---- 线 3：仓库体检（纯 git/du，几十毫秒）----
                stage('仓库体检') {
                    steps {
                        sh '''
                            echo "[体检] 起跑 $(date +%T)"
                            echo "提交总数: $(git rev-list --count HEAD)"
                            echo "最新提交: $(git log --oneline -1)"
                            echo "源码体积: $(du -sh --exclude=.git . | cut -f1)"
                            echo "[体检] 冲线 $(date +%T)"
                        '''
                    }
                }
            }
        }

        // ---- 4. 归档产物 ----
        // 哪边这次真的构建了，就归档哪边（判断依据是前面留的 env 标记）。
        // artifact 路径是相对【工作区根目录】的，不是相对 dir() 进去的那个目录。
        stage('归档产物') {
            steps {
                script {
                    if (env.BACKEND_RAN == 'true') {
                        echo "后端这次构建了，归档 jar"
                        archiveArtifacts artifacts: 'Backend/GameSaves/target/GameSaves-*.jar', allowEmptyArchive: false
                        sh 'ls -lh Backend/GameSaves/target/*.jar'
                        // 有 surefire 报告就挂上，Jenkins 会画出"测试结果趋势图"，
                        // 构建页面左侧会多一个「测试结果」。这次跳过测试的话没有报告，
                        // allowEmptyResults 让它安静跳过（等价于 withMaven 白送的那部分能力）。
                        junit testResults: 'Backend/GameSaves/target/surefire-reports/*.xml', allowEmptyResults: true
                    } else {
                        echo "后端这次没构建，跳过 jar 归档"
                    }

                    if (env.FRONTEND_RAN == 'true') {
                        echo "前端这次构建了，归档 dist"
                        archiveArtifacts artifacts: 'Frontend/GameSaves_Fronted/dist/**', allowEmptyArchive: false
                        sh 'du -sh Frontend/GameSaves_Fronted/dist'
                    } else {
                        echo "前端这次没构建，跳过 dist 归档"
                    }
                }
            }
        }

        // ---- 5. 只在主分支跑 ----
        // when { branch } 读的是 env.BRANCH_NAME，只有多分支任务才有值。
        // 以前在单分支任务里这玩意儿是空的，分支匹配直接返回 false，阶段永远灰着。
        stage('仅主分支') {
            when { branch 'main' }
            steps {
                echo "★ branch 'main' 条件生效 —— 这次是在主分支上跑的"
            }
        }

        // =================================================================
        // 6. 发布确认（人工卡点）
        //
        // 构建本身是自动的，但"要不要发出去"得人来点头。input 就是这个暂停键。
        //
        // ① beforeInput true 必须写。
        //    when 和 input 同时存在时，默认顺序是先弹框、人点完了才去算 when。
        //    那就会出现"让你确认半天，然后告诉你这个阶段被跳过了"的蠢事。
        //    加上 beforeInput，when 先算，不该跑就直接灰掉，根本不弹框。
        //
        // ② timeout 必须写。
        //    没人理它的话流程会永远停在那儿，而且【占着一个执行器】。
        //    一共就 2 个执行器，一次忘记点就能拖垮半边流水线。
        //    超时会被判成 ABORTED（灰色），正好触发 post { aborted }。
        //
        // ③ submitter 写的是【用户 ID】，不是显示名。
        //    登录账号 ID 是 root，显示名是 yingfeng。填 "yingfeng" 不生效，
        //    谁都点不了，只能等超时。多个用逗号分隔。
        // =================================================================
        stage('发布确认') {
            when {
                beforeInput true
                expression {
                    def want = pbool('CONFIRM_DEPLOY', false)
                    echo "[发布确认] 参数原值=${params.CONFIRM_DEPLOY} → 实际按 ${want} 处理"
                    return want
                }
            }

            options {
                timeout(time: 5, unit: 'MINUTES')
            }

            input {
                message "后端 jar 和前端 dist 都好了，确认要发布吗？"
                ok "确认发布"
                submitter "root"
                submitterParameter "APPROVED_BY"   // 点确认的人是谁，塞进这个环境变量
            }

            steps {
                script {
                    // 给后面的部署阶段留一个"已确认"标记。
                    // 部署阶段靠它判断，所以没点确认就绝不会碰服务器。
                    env.DEPLOY_APPROVED = 'true'
                }
                echo "已由 ${env.APPROVED_BY} 确认，进入部署环节"
            }
        }

        // =================================================================
        // 7. 部署到腾讯云 118.25.51.239
        //
        // 三重闸门，缺一不可：
        //   ① 分支必须是 main
        //   ② 必须点过上面那个 input（env.DEPLOY_APPROVED）
        //   ③ 这次真的构建了对应产物（env.BACKEND_RAN / FRONTEND_RAN）
        //      —— 没构建就不能上传，否则会把工作区里上一次留下的旧 jar 传上去
        //
        // 三条来自 docs/superpowers/specs/2026-07-29-backend-deploy-upload-guard-design.md
        // 的教训，这里都落实了：
        //   ① 上传路径带构建号，每次构建都是唯一文件名。那份文档记录的事故就是
        //      两个部署同时写 /tmp/GameSaves.jar.new，SFTP 会话互相卡死。
        //   ② 先传到 /tmp，再从 /tmp 原子 mv 到位。传一半被掐不会污染线上文件。
        //   ③ 并发保护靠 options 里的 disableConcurrentBuilds()，同一个任务不会重入。
        //
        // 另外加了原文没有的：上传大小校验、替换前备份、重启后健康检查、
        // 检查不过自动回滚、旧备份轮转清理。
        // =================================================================
        stage('部署到腾讯云') {
            when {
                allOf {
                    branch 'main'
                    expression { return env.DEPLOY_APPROVED == 'true' }
                }
            }
            options {
                timeout(time: 30, unit: 'MINUTES')
            }
            steps {
                // 用凭据绑定而不是 sshagent：这台 Jenkins 没装 ssh-agent 插件。
                // 私钥会被写到临时文件，构建结束即删；日志里只会显示 ****。
                withCredentials([sshUserPrivateKey(credentialsId: 'tencent-deploy',
                                                   keyFileVariable: 'SSH_KEY',
                                                   usernameVariable: 'SSH_USER')]) {
                    // ⚠ 必须以 #! 开头。
                    // Jenkins 的 sh 步骤默认用 /bin/sh 跑，Ubuntu 上 /bin/sh 是 dash，
                    // 而 dash 【不支持 pipefail】，脚本会在 set 那一行直接 exit 2。
                    // 好在 hudson.tasks.Shell 会检查脚本开头：以 #! 开头时，
                    // 它就把这一行当作解释器来用。所以下面这行既是注释也是开关。
                    // 代价：走 shebang 这条路 Jenkins 不会再自动加 -xe，
                    // 所以 -e 必须自己写进来。
                    sh '''#!/bin/bash
                    set -eu
                    # pipefail 只有 bash 认得。这里用 BASH_VERSION 判断，不能图省事写
                    # `set -o pipefail || true` —— set 是 POSIX 特殊内建命令，它一失败
                    # shell 会直接退出，|| true 救不回来（dash 下实测当场死）。
                    if [ -n "${BASH_VERSION:-}" ]; then set -o pipefail; fi

                    SSH_OPTS="-o BatchMode=yes -o IdentitiesOnly=yes -o StrictHostKeyChecking=accept-new -o ConnectTimeout=20 -o ServerAliveInterval=30 -o ServerAliveCountMax=3"
                    SSH="ssh -i $SSH_KEY $SSH_OPTS"
                    SCP="scp -i $SSH_KEY $SSH_OPTS"
                    TARGET="$SSH_USER@118.25.51.239"

                    TS=$(date +%Y%m%d-%H%M%S)
                    TAG="b${BUILD_NUMBER}"
                    # 这些标记只在对应阶段跑过时才存在。set -u 下直接引用未定义的变量
                    # 会当场报 unbound variable，所以一律用 ${VAR:-默认值} 兜底。
                    DRY="${DEPLOY_DRY_RUN:-true}"

                    echo "════════════════ 部署信息 ════════════════"
                    echo "服务器   : $TARGET"
                    echo "时间戳   : $TS     构建标签: $TAG"
                    echo "确认人   : ${APPROVED_BY:-（未记录）}"
                    echo "提交     : $GIT_COMMIT"
                    echo "演练模式 : $DRY   （true = 只上传校验，不改线上）"
                    echo "══════════════════════════════════════════"

                    # ---------- 后端 ----------
                    if [ "${BACKEND_RAN:-false}" != "true" ]; then
                        echo ""
                        echo "──── 后端：这次没构建，跳过 ────"
                    else
                        JAR=Backend/GameSaves/target/GameSaves-0.0.1-SNAPSHOT.jar
                        if [ ! -f "$JAR" ]; then echo "✗ 找不到 $JAR"; exit 1; fi
                        LOCAL_SIZE=$(stat -c %s "$JAR")
                        REMOTE_TMP="/tmp/GameSaves-${TAG}.jar"

                        echo ""
                        echo "──── 后端 ────"
                        echo "本地 jar   : $JAR  ($LOCAL_SIZE 字节)"
                        echo "远端暂存   : $REMOTE_TMP"

                        echo "[1/3] 上传..."
                        $SCP "$JAR" "$TARGET:$REMOTE_TMP"

                        echo "[2/3] 远端校验并替换..."
                        $SSH "$TARGET" \\
                            REMOTE_TMP="$REMOTE_TMP" LOCAL_SIZE="$LOCAL_SIZE" TS="$TS" DRY="$DRY" \\
                            'bash -s' <<'REMOTE'
set -uo pipefail

EXPECTED=/opt/gamesaving/GameSaves-0.0.1-SNAPSHOT.jar

# 安全闸：确认服务实际在用的 jar 就是我们要替换的那个。对不上就停。
ACTUAL=$(sudo systemctl show -p ExecStart --value gamesaving | tr ' ' '\\n' | grep -E '\\.jar$' | head -1)
echo "    服务实际使用的 jar: $ACTUAL"
if [ "$ACTUAL" != "$EXPECTED" ]; then
    echo "    ✗ 与预期不符（预期 $EXPECTED），停止部署"
    sudo rm -f "$REMOTE_TMP"
    exit 1
fi

# 上传完整性
GOT=$(stat -c %s "$REMOTE_TMP")
if [ "$GOT" != "$LOCAL_SIZE" ]; then
    echo "    ✗ 大小不符：远端 $GOT / 本地 $LOCAL_SIZE，可能传坏了"
    sudo rm -f "$REMOTE_TMP"
    exit 1
fi
echo "    ✓ 上传完整（$GOT 字节）"

if [ "$DRY" = "true" ]; then
    echo "    [演练] 跳过：备份 → sudo cp -a $EXPECTED ${EXPECTED}.bak-${TS}"
    echo "    [演练] 跳过：替换 → sudo mv $REMOTE_TMP $EXPECTED"
    echo "    [演练] 跳过：重启 → sudo systemctl restart gamesaving"
    sudo rm -f "$REMOTE_TMP"
    echo "    ✓ 演练结束，线上未改动（临时文件已清）"
else
    sudo cp -a "$EXPECTED" "${EXPECTED}.bak-${TS}"
    sudo mv "$REMOTE_TMP" "$EXPECTED"
    echo "    ✓ 已替换，旧文件备份为 ${EXPECTED}.bak-${TS}"
    sudo systemctl reset-failed gamesaving
    sudo systemctl restart gamesaving
    echo "    ✓ 已重启"
fi
REMOTE

                        echo "[3/3] 健康检查..."
                        $SSH "$TARGET" TS="$TS" DRY="$DRY" 'bash -s' <<'REMOTE'
set -uo pipefail
EXPECTED=/opt/gamesaving/GameSaves-0.0.1-SNAPSHOT.jar

if [ "$DRY" = "true" ]; then
    echo "    [演练] 跳过：健康检查（线上服务没动过）"
    exit 0
fi

ok=0
for i in $(seq 1 24); do
    if curl -fsS -m 5 http://127.0.0.1:8080/api/games >/dev/null 2>&1; then
        ok=1; echo "    ✓ 第 ${i} 次探测通过（约 $(( (i-1)*5 )) 秒）"; break
    fi
    sleep 5
done

if [ "$ok" != "1" ]; then
    echo "    ✗ 120 秒内没起来 —— 自动回滚"
    sudo mv "$EXPECTED" "${EXPECTED}.failed-${TS}"
    sudo cp -a "${EXPECTED}.bak-${TS}" "$EXPECTED"
    sudo systemctl restart gamesaving
    sleep 20
    if curl -fsS -m 5 http://127.0.0.1:8080/api/games >/dev/null 2>&1; then
        echo "    ✓ 已回滚到旧版本，服务恢复"
    else
        echo "    ✗✗ 回滚后仍不健康，需要人工介入：sudo journalctl -u gamesaving -n 200"
    fi
    exit 1
fi

echo "    线上 jar md5: $(sudo md5sum $EXPECTED | cut -d' ' -f1)"

# 旧备份轮转，只留最近 3 个
cd /opt/gamesaving || exit 0
ls -1t GameSaves-*.jar.bak-* 2>/dev/null | tail -n +4 | while read -r f; do
    sudo rm -f "$f" && echo "    清理旧备份: $f"
done
REMOTE
                    fi

                    # ---------- 前端 ----------
                    if [ "${FRONTEND_RAN:-false}" != "true" ]; then
                        echo ""
                        echo "──── 前端：这次没构建，跳过 ────"
                    else
                        DIST=Frontend/GameSaves_Fronted/dist
                        if [ ! -f "$DIST/index.html" ]; then echo "✗ 找不到 $DIST/index.html"; exit 1; fi
                        REMOTE_TMP="/tmp/gamesaving-web-${TAG}"
                        WEBROOT=/www/wwwroot/gamesaving

                        echo ""
                        echo "──── 前端 ────"
                        echo "本地 dist  : $DIST  ($(du -sh $DIST | cut -f1), $(find $DIST -type f | wc -l) 个文件)"
                        echo "远端暂存   : $REMOTE_TMP"

                        echo "[1/3] 清理远端暂存目录..."
                        $SSH "$TARGET" REMOTE_TMP="$REMOTE_TMP" 'bash -s' <<'REMOTE'
set -euo pipefail
test -n "$REMOTE_TMP"
sudo rm -rf "$REMOTE_TMP"
echo "    已清空 $REMOTE_TMP"
REMOTE

                        echo "[2/3] 上传..."
                        $SCP -r "$DIST" "$TARGET:$REMOTE_TMP"

                        echo "[3/3] 远端校验并切换..."
                        $SSH "$TARGET" \\
                            REMOTE_TMP="$REMOTE_TMP" WEBROOT="$WEBROOT" TS="$TS" DRY="$DRY" \\
                            'bash -s' <<'REMOTE'
set -uo pipefail

# 校验完整性：必须真的有首页，而且文件数对得上
COUNT=$(find "$REMOTE_TMP" -type f 2>/dev/null | wc -l)
if [ ! -s "$REMOTE_TMP/index.html" ] || [ "$COUNT" -lt 5 ]; then
    echo "    ✗ 上传不完整：index.html 缺失或只有 $COUNT 个文件"
    sudo rm -rf "$REMOTE_TMP"
    exit 1
fi
echo "    ✓ 上传完整（$(du -sh $REMOTE_TMP | cut -f1)，$COUNT 个文件）"

if [ "$DRY" = "true" ]; then
    echo "    [演练] 跳过：备份 → sudo mv $WEBROOT ${WEBROOT}.backup-${TS}"
    echo "    [演练] 跳过：切换 → sudo mv $REMOTE_TMP $WEBROOT"
    echo "    [演练] 跳过：chown / nginx -t / reload"
    sudo rm -rf "$REMOTE_TMP"
    echo "    ✓ 演练结束，线上站点未改动"
    exit 0
fi

sudo mv "$WEBROOT" "${WEBROOT}.backup-${TS}"
sudo mv "$REMOTE_TMP" "$WEBROOT"
sudo chown -R www:www "$WEBROOT"
echo "    ✓ 已切换，旧目录备份为 ${WEBROOT}.backup-${TS}"

if ! sudo nginx -t >/dev/null 2>&1; then
    echo "    ✗ nginx 配置检查失败 —— 回滚"
    sudo mv "$WEBROOT" "${WEBROOT}.failed-${TS}"
    sudo mv "${WEBROOT}.backup-${TS}" "$WEBROOT"
    sudo nginx -t >/dev/null 2>&1 && sudo systemctl reload nginx
    exit 1
fi

sudo systemctl reload nginx
echo "    ✓ nginx 已重载"

sleep 2
if sudo -u www test -s "$WEBROOT/index.html"; then
    echo "    ✓ 首页可读（$(sudo stat -c '%U:%G %s 字节' $WEBROOT/index.html)）"
else
    echo "    ✗ 首页不可读 —— 回滚"
    sudo mv "$WEBROOT" "${WEBROOT}.failed-${TS}"
    sudo mv "${WEBROOT}.backup-${TS}" "$WEBROOT"
    sudo systemctl reload nginx
    exit 1
fi

# 旧备份轮转，只留最近 3 个（只清我们自己生成的 backup- 前缀）
cd /www/wwwroot || exit 0
ls -1dt gamesaving.backup-* 2>/dev/null | tail -n +4 | while read -r d; do
    sudo rm -rf "$d" && echo "    清理旧备份: $d"
done
REMOTE
                    fi

                    echo ""
                    echo "════════════════ 部署流程结束 ════════════════"
                    '''
                }
            }
        }
    }

    // =====================================================================
    // post：流水线跑完之后的收尾块，和 stages 平级。
    // 里面每个子块是一个条件，只有条件成立才执行；你可以随便排，
    // 但 Jenkins 实际执行顺序是【固定】的 —— 按插件注册的 ordinal 从大到小：
    //
    //   always(1000) → changed(900) → fixed(890) → regression(880)
    //   → aborted(800) → success(700) → unstable(600) → failure(500)
    //   → cleanup(-10000)
    //
    // 记法：always 永远第一，cleanup 永远最后。
    // 注意 success(700) 排在 failure(500) 前面 —— 和网上很多文章写的不一样。
    //
    // 关键点：post 里失败【不会】改变构建结果，它是善后，不是补救。
    // =====================================================================
    post {
        always {
            echo "┌── post/always ─────────────────────────────────"
            echo "│ 本次结果 : ${currentBuild.currentResult}"
            echo "│ 构建编号 : #${currentBuild.number}  (${currentBuild.displayName})"
            echo "│ 本次耗时 : ${currentBuild.durationString}"
            echo "│ 控制台   : ${currentBuild.absoluteUrl}console"
            echo "└────────────────────────────────────────────────"
        }

        changed {
            echo "★ post/changed    ← 本次结果和上一次【不一样】（上次是 ${currentBuild.previousBuild?.currentResult ?: '无'}）"
        }

        fixed {
            echo "★ post/fixed      ← 上一次是坏结果，这一次修好了"
        }

        regression {
            echo "★ post/regression ← 上一次是好的，这一次搞坏了"
        }

        aborted {
            echo "★ post/aborted    ← 构建被中止了（有人点了中止，或者卡点超时了）"
        }

        success {
            echo "★ post/success    ← 只有成功才跑这里（发通知、打 tag 一般放这）"
        }

        unstable {
            echo "★ post/unstable   ← 测试挂了但没中断流水线（配合 junit 之类的步骤才会出现）"
        }

        failure {
            echo "★ post/failure    ← 只有失败才跑这里"
            echo "  排查第一步：往上翻日志，找【第一个】ERROR，后面的多半是它的连锁反应"
            sh 'ls -lh Backend/GameSaves/target/*.jar 2>/dev/null || echo "  产物不存在 —— 说明编译根本没走完"'
        }

        cleanup {
            echo "★ post/cleanup    ← 最后跑，永远跑（清理、上报放这里最安全）"
            sh 'du -sh Backend/GameSaves/target 2>/dev/null || true'
        }
    }
}
