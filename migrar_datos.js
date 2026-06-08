// Script para migrar datos de CronosTask
// Este script prepara los comandos para inyectar los datos en el localStorage del navegador/Electron

const data = {
  cronostask_history: [{"id":"hist_17804310829058ayzayrlu","name":"Costo de ventas Fredy Fleitas","duration":78404135,"startedAt":1780316369214,"savedAt":1780431082905}],
  cronostask_timers: [{"id":"timer_1780489643423iwf329x1q","name":"Cargando facturas de los clientes","isRunning":true,"startTime":1780768876174,"accumulatedTime":36841469,"initialTime":0,"createdAt":1780489643423,"firstStartTime":1780489643423,"lastStartTime":1780768876174}]
};

console.log("Copia y pega el siguiente código en la consola (F12) de tu nueva aplicación:");
console.log("------------------------------------------------------------------");
for (const [key, value] of Object.entries(data)) {
  console.log(`localStorage.setItem('${key}', '${JSON.stringify(value)}');`);
}
console.log("location.reload();");
console.log("------------------------------------------------------------------");
