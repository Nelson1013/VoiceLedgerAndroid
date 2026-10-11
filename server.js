const http = require('http');
const fs = require('fs');
const path = require('path');

const APK = path.join(__dirname, 'app', 'build', 'outputs', 'apk', 'debug', 'app-debug.apk');
const PORT = 8081;

if (!fs.existsSync(APK)) { console.error('APK not found: ' + APK); process.exit(1); }
const size = fs.statSync(APK).size;
console.log('Serving VoiceLedger.apk (' + size + ' bytes) on port ' + PORT);

http.createServer((req, res) => {
  if (req.url === '/health') { res.writeHead(200); res.end('ok'); return; }
  res.writeHead(200, {
    'Content-Type': 'application/vnd.android.package-archive',
    'Content-Disposition': 'attachment; filename="VoiceLedger.apk"',
    'Content-Length': size
  });
  fs.createReadStream(APK).pipe(res);
}).listen(PORT, () => console.log('READY on ' + PORT));
