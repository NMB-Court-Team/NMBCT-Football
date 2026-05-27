"""
Minecraft 正多边形模型生成器（原始文件，原样保留）
"""
import math
import json
import numpy as np

def normalize(v):
    v = np.array(v, dtype=float)
    n = np.linalg.norm(v)
    if n < 1e-12:
        raise ValueError(f"零向量无法归一化: {v}")
    return v / n

def euler_xyz_from_matrix(R):
    sy = -R[2, 0]
    sy = max(-1.0, min(1.0, sy))
    ry = math.asin(sy)
    if abs(math.cos(ry)) > 1e-6:
        rx = math.atan2(R[2, 1], R[2, 2])
        rz = math.atan2(R[1, 0], R[0, 0])
    else:
        rx = math.atan2(-R[1, 2], R[1, 1])
        rz = 0.0
    return rx, ry, rz

def build_local_frame(normal, tangent=None):
    up = normalize(normal)
    if tangent is not None:
        t = np.array(tangent, dtype=float)
        t = t - np.dot(t, up) * up
        if np.linalg.norm(t) > 1e-12:
            forward = normalize(t)
            right = normalize(np.cross(up, forward))
            return np.column_stack([right, up, forward])
    ref = np.array([0.0, 0.0, 1.0])
    if abs(np.dot(up, ref)) > 0.9:
        ref = np.array([1.0, 0.0, 0.0])
    right   = normalize(np.cross(up, ref))
    forward = np.cross(right, up)
    R = np.column_stack([right, up, forward])
    return R

def get_rectangles_local(n, s):
    r = s / (2 * math.tan(math.pi / n))
    rectangles = []
    if n % 2 == 1:
        for k in range(n):
            theta = 2 * math.pi * k / n
            cx = (r / 2) * math.cos(theta)
            cz = (r / 2) * math.sin(-theta)
            half_w = s / 2
            half_h = r / 2
            angle_deg = math.degrees(theta + math.pi / 2)
            rectangles.append((cx, cz, half_w, half_h, angle_deg))
    else:
        for k in range(n // 2):
            theta = 2 * math.pi * k / n
            cx = 0.0
            cz = 0.0
            half_w = s / 2
            half_h = r
            angle_deg = math.degrees(theta + math.pi / 2)
            rectangles.append((cx, cz, half_w, half_h, angle_deg))
    return rectangles

def make_element(center_world, R_frame, cx_local, cz_local, half_w, half_h,
                 angle_y_local_deg, texture="#texture", uv=None):
    EPS = 0.001
    local_center = np.array([cx_local, 0.0, cz_local])
    origin_world = center_world + R_frame @ local_center
    a = math.radians(angle_y_local_deg)
    R_y = np.array([
        [ math.cos(a), 0, math.sin(a)],
        [ 0,           1, 0           ],
        [-math.sin(a), 0, math.cos(a)]
    ])
    R_total = R_frame @ R_y
    rx, ry, rz = euler_xyz_from_matrix(R_total)
    rx_deg = math.degrees(rx)
    ry_deg = math.degrees(ry)
    rz_deg = math.degrees(rz)
    from_world = origin_world + np.array([-half_w, -EPS, -half_h])
    to_world   = origin_world + np.array([ half_w,  EPS,  half_h])
    def r4(v): return round(float(v), 4)
    element = {
        "from": [r4(from_world[0]), r4(from_world[1]), r4(from_world[2])],
        "to":   [r4(to_world[0]),   r4(to_world[1]),   r4(to_world[2])],
        "rotation": {
            "origin": [r4(origin_world[0]), r4(origin_world[1]), r4(origin_world[2])],
            "x": r4(rx_deg), "y": r4(ry_deg), "z": r4(rz_deg),
        },
        "faces": {"up": {"texture": texture}}
    }
    if uv is not None and len(uv) == 4 and all(isinstance(u, (int, float)) for u in uv):
        element["faces"]["up"]["uv"] = [r4(u) for u in uv]
    return element

def generate_polygon_model(n, center, side_length, normal, texture="#texture", uv=None, tangent=None):
    if n < 3:
        raise ValueError("边数 n 必须 >= 3")
    center = np.array(center, dtype=float)
    s = float(side_length)
    R_frame = build_local_frame(normal, tangent=tangent)
    rectangles = get_rectangles_local(n, s)
    elements = []
    for (cx, cz, half_w, half_h, angle_deg) in rectangles:
        elem = make_element(
            center_world=center, R_frame=R_frame,
            cx_local=cx, cz_local=cz,
            half_w=half_w, half_h=half_h,
            angle_y_local_deg=angle_deg,
            texture=texture, uv=uv
        )
        elements.append(elem)
    return elements
