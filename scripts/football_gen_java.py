"""
football_gen_java.py
====================
Java Edition 足球模型生成器。

结合：
  - ti_geometry.py   → 32 个面的精确位置 / 法线
  - polygon_model.py → 将每个面生成对应的多边形元素列表

输出格式：Java Edition Block/Item Model JSON
  {
    "textures": { "pentagon": "#0", "hexagon": "#1" },
    "elements": [ ... ]   ← 所有 32 个面的多边形片
  }

坐标说明：
  - Java Edition 像素坐标，1 方块 = 16 像素
  - 方块中心 = [8, 8, 8]（像素）
  - ti_geometry 以 origin=(8,8,8) 为球心，inradius 单位即像素

用法：
  python football_gen_java.py
  python football_gen_java.py --inradius 8 --origin 8 8 8 --output football.json
  python football_gen_java.py --inradius 8 --penta-texture "#penta" --hexa-texture "#hexa"
"""

import math
import json
import argparse
import numpy as np

from ti_geometry import get_face_transforms, get_geometry_params
from polygon_model import generate_polygon_model

def _rotate_about_axis(v: np.ndarray, axis: np.ndarray, deg: float) -> np.ndarray:
    """
    使用 Rodrigues 公式将向量 v 绕 axis 旋转 deg（角度制）。
    """
    a = np.array(axis, dtype=float)
    a = a / np.linalg.norm(a)
    x = np.array(v, dtype=float)
    th = math.radians(deg)
    c = math.cos(th)
    s = math.sin(th)
    return x * c + np.cross(a, x) * s + a * np.dot(a, x) * (1 - c)


# ─────────────────────────────────────────────
# 核心：将 ti_geometry 面数据转换为 Java 元素
# ─────────────────────────────────────────────

def face_to_elements(face: dict, edge_len: float,
                     penta_roll_offset_deg: float = -36.0,
                     penta_texture: str = "#pentagon",
                     hexa_texture:  str = "#hexagon") -> list:
    """
    将单个面（来自 ti_geometry.get_face_transforms）转换为
    Java Edition 模型元素列表。

    face 包含：
      center  : 面中心世界坐标（像素）
      normal  : 朝外单位法线
      tangent : 面内切向（用于锁定面内朝向，避免 roll 偏转）
      type    : 'penta' | 'hexa'

    polygon_model.generate_polygon_model 的参数：
      n           : 5 或 6
      center      : face['center']（已经是世界坐标）
      side_length : edge_len（像素）
      normal      : face['normal']（朝外即 up 方向）
      tangent     : face['tangent']（面内参考方向，稳定五/六边形转角）
      texture     : 对应纹理变量
    """
    n       = 5 if face['type'] == 'penta' else 6
    texture = penta_texture if face['type'] == 'penta' else hexa_texture
    tangent = face.get('tangent')
    if tangent is not None and face['type'] == 'penta' and abs(penta_roll_offset_deg) > 1e-12:
        tangent = _rotate_about_axis(
            np.array(tangent, dtype=float),
            np.array(face['normal'], dtype=float),
            penta_roll_offset_deg
        )

    elements = generate_polygon_model(
        n           = n,
        center      = face['center'],
        side_length = edge_len,
        normal      = face['normal'],
        tangent     = tangent,
        texture     = texture,
    )
    return elements


# ─────────────────────────────────────────────
# 验证：检查所有元素的 from/to 在合法范围内
# ─────────────────────────────────────────────

def check_bounds(elements: list, lo: float = -16.0, hi: float = 32.0) -> dict:
    """
    检查所有元素的 from/to 是否在 Java Edition 合法范围 [-16, 32]。
    返回 {'ok': bool, 'violations': list}
    """
    violations = []
    for i, elem in enumerate(elements):
        for key in ('from', 'to'):
            for j, v in enumerate(elem[key]):
                if v < lo or v > hi:
                    violations.append({
                        'elem_index': i,
                        'key': key,
                        'axis': 'xyz'[j],
                        'value': v
                    })
    return {'ok': len(violations) == 0, 'violations': violations}


# ─────────────────────────────────────────────
# 主生成函数
# ─────────────────────────────────────────────

def generate_football_java(
    inradius       : float = 8.0,
    origin         : tuple = (8.0, 8.0, 8.0),
    penta_texture  : str   = "#pentagon",
    hexa_texture   : str   = "#hexagon",
    penta_roll_offset_deg: float = -36.0,
    edge_offset    : float = 0.0,
    output         : str   = "football.json",
    model_id       : str   = "football",
):
    """
    生成完整的 Java Edition 足球模型 JSON。
    """
    params   = get_geometry_params(inradius=inradius)
    edge_len = params['edge_length'] + edge_offset
    if edge_len <= 0:
        raise ValueError(
            f"边长非法：base_edge={params['edge_length']:.6f} + "
            f"edge_offset={edge_offset:.6f} -> {edge_len:.6f}，必须 > 0"
        )

    print(f"=== football_gen_java.py ===")
    print(f"  inradius   = {inradius}")
    print(f"  edge_len   = {edge_len:.6f} px (base={params['edge_length']:.6f}, offset={edge_offset:+.6f})")
    print(f"  penta_roll = {penta_roll_offset_deg:+.3f}°")
    print(f"  outer_r    = {params['outer_radius']:.6f} px")
    print(f"  hexa_r     = {params['hexa_face_radius']:.6f} px  (= inradius)")
    print(f"  penta_r    = {params['penta_face_radius']:.6f} px")
    print(f"  origin     = {origin}")

    # 获取 32 个面的精确变换
    faces = get_face_transforms(inradius=inradius, origin=origin)
    pentas = [f for f in faces if f['type'] == 'penta']
    hexas  = [f for f in faces if f['type'] == 'hexa']
    print(f"  五边形面: {len(pentas)}  六边形面: {len(hexas)}")

    # 生成所有元素
    all_elements = []

    for i, face in enumerate(pentas):
        elems = face_to_elements(face, edge_len, penta_roll_offset_deg, penta_texture, hexa_texture)
        # 给每个元素加注释标记（用 __comment 字段，Blockbench 可识别）
        for j, e in enumerate(elems):
            e['__comment'] = f"penta_{i:02d}_rect{j}"
        all_elements.extend(elems)

    for i, face in enumerate(hexas):
        elems = face_to_elements(face, edge_len, penta_roll_offset_deg, penta_texture, hexa_texture)
        for j, e in enumerate(elems):
            e['__comment'] = f"hexa_{i:02d}_rect{j}"
        all_elements.extend(elems)

    # 统计
    penta_elem_count = len(pentas) * (5 if 5 % 2 == 1 else 5 // 2)
    hexa_elem_count  = len(hexas)  * 3  # 六边形 6 边 → 3 个矩形
    print(f"  五边形元素: {len(pentas)} × 5 = {len(pentas)*5}")
    print(f"  六边形元素: {len(hexas)} × 3 = {len(hexas)*3}")
    print(f"  总元素数  : {len(all_elements)}")

    # 检查坐标范围
    bounds = check_bounds(all_elements)
    if bounds['ok']:
        print(f"  坐标范围检查: ✓ 全部在 [-16, 32] 内")
    else:
        print(f"  ⚠ 坐标范围警告: {len(bounds['violations'])} 个超界值")
        for v in bounds['violations'][:5]:
            print(f"    elem[{v['elem_index']}].{v['key']}.{v['axis']} = {v['value']:.3f}")
        if len(bounds['violations']) > 5:
            print(f"    ... 共 {len(bounds['violations'])} 个")

    # 构建最终 JSON
    model = {
        "__comment": (
            f"Minecraft Football Model | "
            f"inradius={inradius} edge={edge_len:.4f} "
            f"outer_r={params['outer_radius']:.4f} | "
            f"Generated by football_gen_java.py"
        ),
        "textures": {
            "pentagon": penta_texture.lstrip('#'),
            "hexagon":  hexa_texture.lstrip('#'),
        },
        "elements": all_elements
    }

    with open(output, 'w', encoding='utf-8') as f:
        json.dump(model, f, indent=2, ensure_ascii=False)

    print(f"\n✓ 已输出 → {output}  ({len(all_elements)} elements)")
    return model


# ─────────────────────────────────────────────
# 命令行入口
# ─────────────────────────────────────────────

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description='生成 Java Edition 足球模型 JSON')
    parser.add_argument('--inradius',       type=float, default=8.0,
                        help='球心到六边形面的垂直距离，单位像素（默认 8.0）')
    parser.add_argument('--origin',         type=float, nargs=3, default=[8.0, 8.0, 8.0],
                        metavar=('OX','OY','OZ'),
                        help='球心坐标，单位像素（默认 8 8 8）')
    parser.add_argument('--penta-texture',  type=str, default='#pentagon',
                        help='五边形纹理变量（默认 #pentagon）')
    parser.add_argument('--hexa-texture',   type=str, default='#hexagon',
                        help='六边形纹理变量（默认 #hexagon）')
    parser.add_argument('--penta-roll-offset', type=float, default=-36.0,
                        help='五边形绕自身法线额外旋转角度（度，默认 -36）')
    parser.add_argument('--edge-offset',    type=float, default=0.0,
                        help='边长偏移（像素）。最终边长=几何边长+offset；可用负值微调缩小')
    parser.add_argument('--output',         type=str, default='football.json',
                        help='输出文件名（默认 football.json）')
    parser.add_argument('--id',             type=str, default='football',
                        help='模型 ID（仅用于注释）')
    args = parser.parse_args()

    generate_football_java(
        inradius      = args.inradius,
        origin        = tuple(args.origin),
        penta_texture = args.penta_texture,
        hexa_texture  = args.hexa_texture,
        penta_roll_offset_deg = args.penta_roll_offset,
        edge_offset   = 0.0009765625,# args.edge_offset,
        output        = args.output,
        model_id      = args.id,
    )
