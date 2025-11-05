import React from 'react';

export default function Toolbar({ nodes, flows }) {
  const exportJson = () => {
    const processJson = {
      name: "Meu Processo DNA",
      definitionJson: { nodes, flows }
    };
    const blob = new Blob([JSON.stringify(processJson, null, 2)], { type: 'application/json' });
    const link = document.createElement('a');
    link.href = URL.createObjectURL(blob);
    link.download = 'process.json';
    link.click();
  };

  const deploy = async () => {
    await fetch('https://seu-endpoint-bpm/deploy', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ nodes, flows })
    });
    alert('Deploy feito!');
  };

  return (
    <div style={{ padding: '5px', borderBottom: '1px solid #ccc', background: '#eee' }}>
      <button onClick={exportJson}>Export JSON</button>
      <button onClick={deploy} style={{ marginLeft: '10px' }}>Deploy</button>
    </div>
  );
}
