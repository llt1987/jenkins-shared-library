from fastapi import FastAPI, WebSocket
from fastapi.staticfiles import StaticFiles
from fastapi.responses import FileResponse
import asyncio

app = FastAPI()

# Serve React build
app.mount("/assets", StaticFiles(directory="frontend-dist/assets"), name="assets")

@app.get("/")
def serve_frontend():
    return FileResponse("frontend-dist/index.html")

@app.websocket("/ws")
async def websocket_endpoint(ws: WebSocket):
    await ws.accept()
    while True:
        data = await ws.receive_text()
        response = f"AI is typing: I received '{data}'"
        for char in response:
            await ws.send_text(char)
            await asyncio.sleep(0.05)  # typing effect

