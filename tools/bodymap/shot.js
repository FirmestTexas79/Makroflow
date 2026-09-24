const { chromium } = require('playwright');
(async () => { const b = await chromium.launch(); const p = await b.newPage({viewport:{width:880,height:820}});
await p.goto('' + 'file://' + __dirname + '/body.html' + ''); await p.screenshot({path: __dirname + '/body.png'}); await b.close(); })();
