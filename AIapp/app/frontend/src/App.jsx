import { useState, useEffect } from "react";

function App() {
  const [msg, setMsg] = useState("");
  const [reply, setReply] = useState("");
  const [ws, setWs] = useState(null);

  useEffect(() => {
    const socket = new WebSocket("ws://localhost:3000/ws");
    socket.onmessage = (event) => {
      setReply(prev => prev + event.data); // append streamed chars
    };
    setWs(socket);
    return () => socket.close();
  }, []);

  const sendMessage = () => {
    setReply("");
    ws.send(msg);
  };

  return (
    <div style={{ padding: "20px", fontFamily: "Arial, sans-serif" }}>
      <h1>Interactive AI Chat</h1>
      <div style={{ border: "1px solid #ccc", padding: "10px", minHeight: "200px" }}>
        <p>{reply}</p>
      </div>
      <input
        style={{ marginTop: "10px", width: "70%" }}
        value={msg}
        onChange={e => setMsg(e.target.value)}
      />
      <button onClick={sendMessage} style={{ marginLeft: "10px" }}>Send</button>
    </div>
  );
}

export default App;

