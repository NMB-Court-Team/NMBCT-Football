"""
Minecraft 正多边形模型生成器

根据边数、中心、边长、法向量，生成 Minecraft 模型元素 JSON 列表。

旋转约定（来自 Wiki）：
- 支持三轴旋转（x, y, z），旋转顺序为 x -> y -> z
- 均符合右手螺旋定则
- 单位：像素（1方块 = 16像素），坐标范围 [-16, 32]
"""

import math
import json
import numpy as np


# ────────────────────────────────────────────────
# 数学工具
# ────────────────────────────────────────────────

def normalize(v):
    v = np.array(v, dtype=float)
    n = np.linalg.norm(v)
    if n < 1e-12:
        raise ValueError(f"零向量无法归一化: {v}")
    return v / n


def euler_xyz_from_matrix(R):
    """
    从旋转矩阵（x->y->z 顺序，即 R = Rz @ Ry @ Rx）反解欧拉角（弧度）。
    返回 (rx, ry, rz)，其中 ry ∈ [-π/2, π/2]。
    处理万向节死锁情况（|ry| = π/2）。
    """
    sy = -R[2, 0]
    sy = max(-1.0, min(1.0, sy))
    ry = math.asin(sy)

    if abs(math.cos(ry)) > 1e-6:
        rx = math.atan2(R[2, 1], R[2, 2])
        rz = math.atan2(R[1, 0], R[0, 0])
    else:
        # 万向节死锁
        rx = math.atan2(-R[1, 2], R[1, 1])
        rz = 0.0

    return rx, ry, rz


def build_local_frame(normal):
    """
    给定法向量 normal（多边形平面的法线，即立方体 up 面最终朝向），
    构造**右手系**局部坐标系，返回旋转矩阵 R（列为 [right, up, forward]）：
        世界坐标 = R @ 局部坐标

    必须保证 det(R) = +1（纯旋转，无反射），否则欧拉角分解会引入额外翻转。

    构造方式：
        right   = normalize(up × ref)   # up 叉乘参考向量
        forward = right × up            # 保持右手系
    """
    up = normalize(normal)

    # 选一个与 up 不平行的参考向量
    ref = np.array([0.0, 0.0, 1.0])
    if abs(np.dot(up, ref)) > 0.9:
        ref = np.array([1.0, 0.0, 0.0])

    right   = normalize(np.cross(up, ref))   # up × ref → 右手系
    forward = np.cross(right, up)            # right × up → 保持右手系

    R = np.column_stack([right, up, forward])  # det = +1
    return R


# ────────────────────────────────────────────────
# 核心：生成正多边形的矩形列表（局部坐标系）
# ────────────────────────────────────────────────

def get_rectangles_local(n, s):
    """
    在局部坐标系中生成覆盖正n边形的矩形列表。
    局部坐标系：多边形在 xz 平面（y=0），中心在原点。

    返回列表，每项为 (cx, cz, half_w, half_h, angle_deg)：
        cx, cz    : 矩形中心在 xz 平面的位置（像素）
        half_w    : 矩形"宽"方向（沿边方向）的半长
        half_h    : 矩形"高"方向（垂直边指向内侧）的半长
        angle_deg : 矩形"宽"方向与局部 x 轴的夹角（度），
                    等价于绕局部 y 轴的旋转角

    内切圆半径 r = s / (2·tan(π/n))

    n 为奇数：n 个矩形，每个从一条边延伸到中心
    n 为偶数：n/2 个矩形，每个横跨一对平行边
    """
    r = s / (2 * math.tan(math.pi / n))  # 内切圆半径

    rectangles = []

    if n % 2 == 1:
        # 奇数：n 个矩形
        # 矩形宽=s，高=r，中心距多边形中心 r/2
        for k in range(n):
            theta = 2 * math.pi * k / n      # 第k条边中点的方位角
            cx = (r / 2) * math.cos(theta)
            cz = (r / 2) * math.sin(-theta)
            half_w = s / 2
            half_h = r / 2
            # "宽"方向沿边的切线，即 theta + π/2
            angle_deg = math.degrees(theta + math.pi / 2)
            rectangles.append((cx, cz, half_w, half_h, angle_deg))
    else:
        # 偶数：n/2 个矩形
        # 矩形宽=s，高=2r，中心在多边形中心
        for k in range(n // 2):
            theta = 2 * math.pi * k / n
            cx = 0.0
            cz = 0.0
            half_w = s / 2
            half_h = r            # 半个对边距离
            angle_deg = math.degrees(theta + math.pi / 2)
            rectangles.append((cx, cz, half_w, half_h, angle_deg))

    return rectangles


# ────────────────────────────────────────────────
# 将矩形转换为 Minecraft 模型元素
# ────────────────────────────────────────────────

def make_element(center_world, R_frame, cx_local, cz_local, half_w, half_h,
                 angle_y_local_deg, texture="#texture", uv=None):
    """
    生成一个 Minecraft 模型元素（只渲染 up 面）。

    流程：
        1. 矩形自身坐标：宽沿 x，高沿 z，厚度 ±EPS 沿 y
        2. 复合旋转：R_total = R_frame @ R_y(angle_y_local_deg)
           - R_y 将矩形在局部 xz 平面内转到正确朝向
           - R_frame 将局部坐标系变换到世界坐标系
        3. 旋转原点 = 矩形中心（世界坐标）
        4. from/to 是矩形在旋转前（以旋转原点为参考）的坐标
        5. 从 R_total 提取欧拉角写入 rotation 字段

    注意：R_frame 必须是行列式为 +1 的纯旋转矩阵，
    否则 euler_xyz_from_matrix 分解出的欧拉角会引入意外翻转。
    """
    EPS = 0.001  # 极小厚度，使 up 面存在

    # ── 矩形中心的世界坐标 ──
    local_center = np.array([cx_local, 0.0, cz_local])
    origin_world = center_world + R_frame @ local_center

    # ── 复合旋转矩阵 ──
    a = math.radians(angle_y_local_deg)
    R_y = np.array([
        [ math.cos(a), 0, math.sin(a)],
        [ 0,           1, 0           ],
        [-math.sin(a), 0, math.cos(a)]
    ])
    R_total = R_frame @ R_y  # det = det(R_frame) * det(R_y) = 1 * 1 = 1

    # ── 提取欧拉角 ──
    rx, ry, rz = euler_xyz_from_matrix(R_total)
    rx_deg = math.degrees(rx)
    ry_deg = math.degrees(ry)
    rz_deg = math.degrees(rz)

    # ── from / to（矩形自身坐标，旋转前，以旋转原点为参考）──
    from_world = origin_world + np.array([-half_w, -EPS, -half_h])
    to_world   = origin_world + np.array([ half_w,  EPS,  half_h])

    def r4(v):
        return round(float(v), 4)

    element = {
        "from": [r4(from_world[0]), r4(from_world[1]), r4(from_world[2])],
        "to":   [r4(to_world[0]),   r4(to_world[1]),   r4(to_world[2])],
        "rotation": {
            "origin": [r4(origin_world[0]), r4(origin_world[1]), r4(origin_world[2])],
            "x": r4(rx_deg),
            "y": r4(ry_deg),
            "z": r4(rz_deg),
        },
        "faces": {
            "up": {
                "texture": texture,
            }
        }
    }

    # uv应为一个大小为4的浮点数列表
    if uv is not None and len(uv) == 4 and all(isinstance(u, (int, float)) for u in uv):
        element["faces"]["up"]["uv"] = [r4(u) for u in uv]
    return element


# ────────────────────────────────────────────────
# 主函数
# ────────────────────────────────────────────────

def generate_polygon_model(n, center, side_length, normal, texture="#texture", uv=None):
    """
    生成正n边形的 Minecraft 模型元素列表。

    参数：
        n           : 正多边形边数（整数，n >= 3）
        center      : 正多边形中心（像素坐标，列表或数组 [x, y, z]）
                      Minecraft 像素坐标：方块中心为 [8, 8, 8]
        side_length : 正多边形边长（像素）
        normal      : 正多边形平面法向量（列表或数组，无需归一化）
                      法向量指向多边形"朝上"的一侧（即 up 面朝向）
        texture     : 纹理变量名（默认 "#texture"）

    返回：
        list of dict，每个 dict 是一个 Minecraft 模型元素
    """
    if n < 3:
        raise ValueError("边数 n 必须 >= 3")

    center = np.array(center, dtype=float)
    s = float(side_length)

    R_frame = build_local_frame(normal)
    rectangles = get_rectangles_local(n, s)

    elements = []
    for (cx, cz, half_w, half_h, angle_deg) in rectangles:
        elem = make_element(
            center_world=center,
            R_frame=R_frame,
            cx_local=cx,
            cz_local=cz,
            half_w=half_w,
            half_h=half_h,
            angle_y_local_deg=angle_deg,
            texture=texture,
            uv=uv
        )
        elements.append(elem)

    return elements


if __name__ == "__main__":
    # 生成六边形
    n = 5
    center = [8, 0, 8]
    side_length = 8
    normal = [1, 1, 1]
    elements = generate_polygon_model(n, center, side_length, normal)
    print(json.dumps(elements, indent=4))
