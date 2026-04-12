import pygame
import sys
import threading
from flask import Flask, request

# -----------------------------
# HUD DATA (will be updated live)
# -----------------------------
hud_data = {
    "speed": "65",
    "mpg": "32",
    "range": "210",
    "turn": "Turn Right",
    "distance": "1.5 miles"
}

# -----------------------------
# Flask Server Setup
# -----------------------------
app = Flask(__name__)

@app.route('/update', methods=['POST'])
def update_data():
    global hud_data
    data = request.get_json(silent=True) or {}

    if "speed" in data:
        hud_data["speed"] = data["speed"]

    if "mpg" in data:
        hud_data["mpg"] = data["mpg"]

    if "range" in data:
        hud_data["range"] = data["range"]

    if "turn" in data:
        hud_data["turn"] = data["turn"]

    if "distance" in data:
        hud_data["distance"] = data["distance"]

    return "OK"

@app.route('/state', methods=['GET'])
def get_state():
    return hud_data

def run_server():
    app.run(host='0.0.0.0', port=5000)

# Run server in background thread
threading.Thread(target=run_server, daemon=True).start()

# -----------------------------
# Pygame Renderer
# -----------------------------
pygame.init()

WIDTH, HEIGHT = 1280, 720
screen = pygame.display.set_mode((WIDTH, HEIGHT), pygame.FULLSCREEN)
pygame.display.set_caption("HUD Renderer")

BLACK = (0, 0, 0)
GREEN = (0, 255, 120)

font_large = pygame.font.SysFont("Arial", 60)
font_medium = pygame.font.SysFont("Arial", 40)

while True:
    screen.fill(BLACK)

    # LEFT PANEL
    speed_text = font_large.render(hud_data["speed"] + " MPH", True, GREEN)
    screen.blit(speed_text, (40, 60))

    mpg_text = font_medium.render("MPG: " + hud_data["mpg"], True, GREEN)
    screen.blit(mpg_text, (40, 140))

    range_text = font_medium.render("Range: " + hud_data["range"] + " mi", True, GREEN)
    screen.blit(range_text, (40, 200))

    # CENTER PANEL
    pygame.draw.rect(screen, GREEN, (480, 120, 320, 240), 3)

    road_text = font_medium.render("Lane Visualization", True, GREEN)
    screen.blit(road_text, (510, 150))

    # RIGHT PANEL
    nav_text = font_large.render(hud_data["turn"], True, GREEN)
    screen.blit(nav_text, (900, 60))

    distance_text = font_medium.render(hud_data["distance"], True, GREEN)
    screen.blit(distance_text, (900, 140))

    pygame.display.flip()

    for event in pygame.event.get():
        if event.type == pygame.KEYDOWN:
            if event.key == pygame.K_ESCAPE:
                pygame.quit()
                sys.exit()
