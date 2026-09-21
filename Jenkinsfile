// =====================================================================
// minesave 的 Jenkins 流水线定义
//
// 这个文件本身就是"构建配置" —— 它和代码一起存在 Git 里。
// 这是 Pipeline 相对 Freestyle 最大的价值：构建流程可版本化、可 review、
// 可复制到另一台 Jenkins，不用再对着界面点一遍。
//
// v3 新增两件事：
//   ① when     —— 阶段可以"有条件地跳过"
//   ② parallel —— 多条线同时跑（外加 failFast）
// =====================================================================
pipeline {
    agent any                       // 在任意可用执行器上跑（现在只有内置节点 Jenkins）

    options {
        buildDiscarder(logRotator(numToKeepStr: '20'))   // 只留最近 20 次构建，别把磁盘撑爆
        disableConcurrentBuilds()                        // 禁止同一个任务并发：工作区是共用的
    }

    // -----------------------------------------------------------------
    // 参数：写在代码里，不再在界面上手点。
    // 注意 ── 新增的参数要等"包含它的那次构建"跑完，才会出现在界面上。
    // -----------------------------------------------------------------
    parameters {
        booleanParam(name: 'SKIP_TESTS',    defaultValue: true,  description: '跳过单元测试。本机没起 MySQL/Meilisearch 时必须勾上，否则测试大面积报错')
        booleanParam(name: 'CLEAN_FIRST',   defaultValue: true,  description: '先执行 mvn clean 从零编译。取消勾选走增量，快很多')
        booleanParam(name: 'SKIP_FRONTEND', defaultValue: false, description: '跳过"前端测试"这条并行分支（用来演示 when 条件）')
        string(name: 'BUILD_NOTE', defaultValue: '', description: '随手写一句备注，会打印到日志里，方便事后区分这次构建是干嘛的')
    }

    // -----------------------------------------------------------------
    // 触发器：等价于界面上的"轮询 SCM"（Poll SCM）。
    //   H   = hash。Jenkins 按任务名算出一个固定的分钟偏移，避免所有任务挤在同一分钟轮询。
    //   /2  = 每 2 分钟一次。生产上一般写 H/5 或 H/15。
    // -----------------------------------------------------------------
    triggers {
        pollSCM('H/2 * * * *')
    }

    environment {
        // ① 修掉"JAVA_HOME 是 21、java 却是 25"这个矛盾。
        //    minesave 的编译目标是 Java 17，这里显式钉住 17，和 CI/生产保持一致。
        JAVA_HOME = '/usr/lib/jvm/java-17-openjdk-amd64'

        // ② 修掉"mvn 不在 PATH 里"。
        //    Jenkins 不读 ~/.bashrc，所以 .bashrc 里配的在这里一律无效，必须显式声明。
        PATH = "/home/yf/opt/maven/bin:/usr/lib/jvm/java-17-openjdk-amd64/bin:${env.PATH}"
    }

    stages {

        // ============ 第 1 阶段：把仓库代码拉到工作区 ============
        stage('取代码') {
            steps {
                checkout scm
                sh 'echo "工作区: $WORKSPACE"'
                sh 'git log --oneline -1'
                sh 'git rev-parse --short HEAD'
            }
        }

        // ============ 第 2 阶段：确认工具链真的就位 ============
        stage('工具链体检') {
            steps {
                sh '''
                    echo "JAVA_HOME = $JAVA_HOME"
                    echo -n "java : "; java -version 2>&1 | head -1
                    echo -n "mvn  : "; mvn -version 2>&1 | head -1
                    echo -n "node : "; node -v
                '''
            }
        }

        // =============================================================
        // 第 3 阶段：侦察分支名
        // 这一步是给下面 when { branch } 的演示铺垫：先看清楚这几个变量到底有没有值。
        // =============================================================
        stage('分支名侦察') {
            steps {
                sh '''
                    echo "BRANCH_NAME      = [${BRANCH_NAME}]"
                    echo "GIT_LOCAL_BRANCH = [${GIT_LOCAL_BRANCH}]"
                    echo "GIT_BRANCH       = [${GIT_BRANCH}]"
                '''
            }
        }

        // =================================================================
        // 第 4 阶段：并行区 —— 三条线同时开跑
        //
        // parallel 的语义（这是最容易被误解的一点）：
        //   · 整个 Pipeline 只占【1 个执行器】。分支之间是同一个构建内部的
        //     并发线程，共用同一个工作区，不会各自再占一个执行器。
        //   · 正因为共用工作区，分支之间【不能写同一个文件】。这里三条线
        //     各写各的目录，互不干扰。
        //   · failFast true：任意一条线失败，立刻掐掉还在跑的其它线。
        //     （不写的话默认会等所有线跑完，再统一判定失败。）
        // =================================================================
        stage('并行构建') {
            failFast true

            parallel {

                // ---- 线 1：后端打包（最慢，约 8 秒）----
                stage('后端打包') {
                    steps {
                        script {
                            // ---- 读参数 ----
                            // 坑：在"首次引入某个参数"的那次构建里，params.X 是 null
                            //     （构建启动时任务上还没有这个参数定义，是这次运行过程中才注册上去的）。
                            //     所以这里显式判 null 兜默认值。
                            //
                            //     千万不要图省事写  params.SKIP_TESTS ?: true
                            //     Groovy 里 false ?: true 的结果是 true —— 用户取消勾选反而会被当成勾上。
                            def skipTests  = (params.SKIP_TESTS  == null) ? true : params.SKIP_TESTS
                            def cleanFirst = (params.CLEAN_FIRST == null) ? true : params.CLEAN_FIRST

                            // ---- 用参数拼命令 ----
                            // 这就是 Pipeline 比 Freestyle 强的地方：真的是在写程序，不是在拼字符串。
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
                }

                // ---- 线 2：前端测试（很快，约 0.2 秒；用 when 控制跑不跑）----
                stage('前端测试') {
                    when {
                        expression {
                            // 同上一节的坑：新增参数在"首次引入它的那次构建"里是 null。
                            // null 兜底成 false，也就是默认【要跑】前端测试。
                            def skip = (params.SKIP_FRONTEND == null) ? false : params.SKIP_FRONTEND
                            echo "[前端] when 求值：SKIP_FRONTEND=${skip}（null → false）"
                            return !skip
                        }
                    }
                    steps {
                        sh 'echo "[前端] 起跑 $(date +%T)"'
                        dir('Frontend/GameSaves_Fronted') {
                            // 纯 node 内置测试，不需要 node_modules，所以这里不用先 npm install
                            sh 'node --test test/*.test.mjs'
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

        // ============ 第 5 阶段：归档产物（产物不在就跳过）============
        stage('归档产物') {
            when {
                // 用 when 兜住"后端失败导致没有 jar"的情况，
                // 免得归档报错把真正的失败原因盖住。
                // 注意 fileExists 不支持通配符，必须写准确路径。
                expression { return fileExists('Backend/GameSaves/target/GameSaves-0.0.1-SNAPSHOT.jar') }
            }
            steps {
                // artifact 路径是相对【工作区根目录】的，不是相对 dir() 进去的目录
                archiveArtifacts artifacts: 'Backend/GameSaves/target/GameSaves-*.jar', allowEmptyArchive: false
                sh 'ls -lh Backend/GameSaves/target/*.jar'
            }
        }

        // =================================================================
        // 【教学演示】when { branch } 的经典陷阱
        //
        // 网上教程到处是 when { branch 'main' }，但它【只在多分支流水线
        // (Multibranch Pipeline) 里有用】。普通 Pipeline 任务没有 BRANCH_NAME，
        // 这个条件恒为 false —— 阶段被静默跳过，而且【不算失败】。
        //
        // 这个阶段是故意留着的，正常情况下它在流水线图里永远是灰的（skipped）。
        // 看完效果就可以删掉它。
        // =================================================================
        stage('演示：branch条件在普通任务里恒为假') {
            when { branch 'main' }
            steps {
                echo "看到这一行说明 branch 条件居然生效了，请回头看上面『分支名侦察』的输出"
            }
        }
    }
}
RONTEND}（null 按 false 处理）→ skip=${skip}"
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
