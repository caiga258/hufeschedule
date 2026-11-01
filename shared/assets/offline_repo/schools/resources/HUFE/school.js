// 湖南财政经济学院 · 统一身份认证(CAS) + 正方教务系统(jsxsd) 适配脚本
//
// 使用前提（App 内 WebView）：登录后进入「培养服务 → 我的课表 → 学期理论课表」，再点“执行导入”
//
// 依赖的桥接 API（由 App 注入）：
//   window.shiguangBridgePromise.savePresetTimeSlots(json)  -> 导入该课表的作息（基础时间段）
//   window.shiguangBridgePromise.saveImportedCourses(json)  -> 导入课程
//   window.shiguangBridgePromise.showAlert(title, msg, ok)  -> 模态提示
//   shiguangBridge.showToast(msg) / shiguangBridge.notifyTaskCompletion()

(async function hufeImport() {
    const toast = (m) => {
        try { window.shiguangBridge && shiguangBridge.showToast(m); } catch (e) { console.log(m); }
    };
    const alert = async (title, msg) => {
        try {
            if (window.shiguangBridgePromise) await window.shiguangBridgePromise.showAlert(title, msg, '知道了');
        } catch (e) { /* 忽略 */ }
    };

    try {
        // ================= 1. 学校作息（12 节） =================
        // 来源：湖南财政经济学院主校区上课作息时间
        const PERIODS = [
            { number: 1,  startTime: '08:00', endTime: '08:45' },
            { number: 2,  startTime: '08:55', endTime: '09:40' },
            { number: 3,  startTime: '10:00', endTime: '10:45' },
            { number: 4,  startTime: '10:45', endTime: '11:30' },
            { number: 5,  startTime: '14:00', endTime: '14:45' },
            { number: 6,  startTime: '14:55', endTime: '15:40' },
            { number: 7,  startTime: '15:50', endTime: '16:35' },
            { number: 8,  startTime: '16:45', endTime: '17:30' },
            { number: 9,  startTime: '18:30', endTime: '19:15' },
            { number: 10, startTime: '19:25', endTime: '20:10' },
            { number: 11, startTime: '20:20', endTime: '21:05' },
            { number: 12, startTime: '21:15', endTime: '22:00' },
        ];

        // 第 3、4 节按教学楼错峰（其余节次全校统一）。
        // App 会根据课程地点（教室号首位数字）在课表、提醒、小组件里自动换算实际时间，
        // 所以这里只导入逻辑节次和地点，不要提前写入自定义时间，
        // 否则课程块会被真实分钟数推移，反而和节次网格错位。

        // ================= 2. 定位课表 =================
        // 只有“表头含星期一”的表格才算真课表，避免误判首页的课表小部件
        function isTimetable(t) {
            if (!t || !t.rows || t.rows.length < 2) return false;
            const head = Array.from(t.rows[0].cells)
                .map((c) => c.innerText.replace(/\s/g, ''))
                .join('|');
            return /星期[一二三四五六日天]/.test(head);
        }

        function findTable() {
            const docs = [document];
            for (const f of document.querySelectorAll('iframe')) {
                try {
                    if (f.contentDocument && f.contentDocument.body) docs.push(f.contentDocument);
                } catch (e) { /* 跨域 iframe 忽略 */ }
            }
            for (const d of docs) {
                for (const sel of ['table.qz-weeklyTable', '#kbtable', 'table']) {
                    for (const t of d.querySelectorAll(sel)) {
                        if (isTimetable(t)) return t;
                    }
                }
            }
            return null;
        }

        // ================= 3. 解析工具 =================
        // "0102节" -> [1, 2]
        function sectionsFromLabel(label) {
            const m = String(label).replace(/\s/g, '').match(/^(\d{2})(\d{2})/);
            if (m) return [parseInt(m[1], 10), parseInt(m[2], 10)];
            const n = String(label).match(/(\d+)/);
            return n ? [parseInt(n[1], 10), parseInt(n[1], 10)] : [1, 1];
        }

        // "1-2,4-16周(单)" -> [1,2,5,7,...]
        function parseWeeks(text) {
            const weeks = new Set();
            const oddOnly = /单/.test(text);
            const evenOnly = /双/.test(text);
            const core = String(text).split('周')[0] || '';
            core.split(/[,，]/).forEach((seg) => {
                const [a, b] = seg.split('-').map((x) => parseInt(x, 10));
                const start = a || 1;
                const end = b || start;
                for (let w = start; w <= end; w++) {
                    if (oddOnly && w % 2 === 0) continue;
                    if (evenOnly && w % 2 === 1) continue;
                    weeks.add(w);
                }
            });
            return Array.from(weeks).sort((x, y) => x - y);
        }

        // "老师:朱敏丽讲师;时间:1-8周[1-2节];地点:三教学楼(2304)"
        function parseDetail(text) {
            const out = { teacher: '', time: '', position: '' };
            String(text).split(/[;；]/).forEach((part) => {
                const s = part.trim();
                if (/^老师[:：]/.test(s)) out.teacher = s.replace(/^老师[:：]/, '').trim();
                else if (/^时间[:：]/.test(s)) out.time = s.replace(/^时间[:：]/, '').trim();
                else if (/^地点[:：]/.test(s)) out.position = s.replace(/^地点[:：]/, '').trim();
            });
            return out;
        }

        // ================= 4. 找不到课表：给出正确入口提示 =================
        const table = findTable();

        if (!table) {
            if (/cas\/login|uia\.hufe/i.test(location.href)) {
                await alert('还没登录', '请先在本页登录统一身份认证，登录后再点一次“执行导入”。');
            } else if (/jiaowu\.hufe\.edu\.cn/.test(location.href)) {
                await alert(
                    '请先打开课表页',
                    '导入前请在教务系统中依次进入：\n培养服务 → 我的课表 → 学期理论课表\n\n' +
                    '页面出现带“星期一…星期日”的课表格子后再点“执行导入”。\n' +
                    '（点“知道了”后我会帮你自动跳到该页面）'
                );
                toast('正在跳转到「学期理论课表」…');
                location.href = '/jsxsd/xskb/xskb_list.do';
            } else {
                await alert('页面不对', '请先在 App 内打开学校教务系统，进入“学期理论课表”后再点执行导入。');
            }
            return;
        }

        // ================= 5. 遍历表格 =================
        const rows = Array.from(table.rows);

        // 表头确定“第几列是星期几”
        const dayOfCol = {};
        Array.from(rows[0].cells).forEach((cell, idx) => {
            const t = cell.innerText.replace(/\s/g, '');
            const m = t.match(/^星期([一二三四五六日天])$/);
            if (!m) return;
            const map = { 一: 1, 二: 2, 三: 3, 四: 4, 五: 5, 六: 6, 日: 7, 天: 7 };
            dayOfCol[idx] = map[m[1]];
        });

        const courses = [];

        for (let r = 1; r < rows.length; r++) {
            const row = rows[r];
            const [rowStart] = sectionsFromLabel(row.cells[0] ? row.cells[0].innerText : '');

            for (let c = 0; c < row.cells.length; c++) {
                const day = dayOfCol[c];
                if (!day) continue; // 跳过“节次”列和“备注”列

                const items = row.cells[c].querySelectorAll('li.courselists-item');

                Array.from(items).forEach((li) => {
                    const titleEl = li.querySelector('.qz-hasCourse-title');
                    const name = (titleEl ? titleEl.innerText : '').replace(/\s+/g, ' ').trim();
                    if (!name) return;

                    const detailText = Array.from(li.querySelectorAll('.qz-hasCourse-detailitem'))
                        .map((s) => s.innerText)
                        .join(';')
                        || li.innerText;

                    const d = parseDetail(detailText);

                    // 节次优先取详情里的 [x-y节]，否则用行标签
                    const sec = d.time.match(/\[(\d+)-(\d+)节\]/);
                    const startSection = sec ? parseInt(sec[1], 10) : rowStart;
                    const endSection = sec ? parseInt(sec[2], 10) : rowStart;

                    const weeks = d.time ? parseWeeks(d.time) : [1];
                    if (!weeks.length) return;

                    const course = {
                        name: name,
                        teacher: d.teacher,
                        position: d.position,
                        day: day,
                        startSection: startSection,
                        endSection: endSection,
                        weeks: weeks,
                    };

                    courses.push(course);
                });
            }
        }

        if (!courses.length) {
            await alert('没有解析到课程', '页面里没找到课程块。请确认当前是“学期理论课表”页面，且本周有课。');
            return;
        }

        console.log('解析到 ' + courses.length + ' 条课程（第 3/4 节将由 App 按教室号自动错峰显示）', courses);

        // ================= 6. 先导入作息，再导入课程 =================
        let slotOk = false;
        try {
            slotOk = await window.shiguangBridgePromise.savePresetTimeSlots(JSON.stringify(PERIODS));
        } catch (e) {
            console.warn('作息导入失败（不影响课程）:', e);
        }

        const ok = await window.shiguangBridgePromise.saveImportedCourses(JSON.stringify(courses));

        if (ok === true) {
            toast(
                '导入成功：' + courses.length + ' 节' +
                (slotOk ? '，作息已同步' : '') +
                '（第 3/4 节将按教室号自动错峰显示）'
            );
            setTimeout(() => {
                try { shiguangBridge.notifyTaskCompletion(); } catch (e) {}
            }, 900);
        } else {
            toast('导入失败：' + ok);
        }
    } catch (err) {
        console.error('HUFE 适配脚本异常:', err);
        toast('脚本出错：' + (err && err.message ? err.message : err));
    }
})();
