"""
ti_geometry.py
==============
截角二十面体 (Truncated Icosahedron) 精确几何数据。

生成 32 个面的完整 transform：
  - face_center   : 面中心坐标（在单位球上缩放到给定 inradius）
  - normal        : 面法线（朝外）
  - tangent       : 面内切向（用于确定 roll/twist，与邻接一致）
  - bitangent     : normal × tangent
  - face_type     : 'penta' | 'hexa'

坐标约定：
  - 所有面中心 / 法线基于截角二十面体的标准顶点公式推导
  - 面中心 = 面所有顶点的平均值，再归一化
  - 切向 = 从面中心指向第一条边中点（保证邻接 roll 一致）

用法：
  from ti_geometry import get_face_transforms
  faces = get_face_transforms(inradius=8.0, origin=(8,8,8))
  # faces[i] = {type, center, normal, tangent, bitangent, rotation_matrix}
"""

import math
import numpy as np

# ─────────────────────────────────────────────
# 1. 黄金比例 & 截角二十面体顶点
# ─────────────────────────────────────────────
PHI = (1 + math.sqrt(5)) / 2

def _norm(v):
    v = np.array(v, dtype=float)
    return v / np.linalg.norm(v)

def _truncated_icosahedron_vertices():
    """
    返回截角二十面体的 60 个顶点（边长=1 的标准版本）。
    顶点来源：Wikipedia 截角二十面体标准坐标。
    所有偶置换 of (0, ±1, ±3φ), (±1, ±(2+φ), ±2φ), (±2, ±(1+2φ), ±φ)
    """
    p = PHI
    raw = []

    def even_perms(a, b, c):
        """所有偶置换"""
        return [(a,b,c),(b,c,a),(c,a,b)]

    def sign_combos(a, b, c):
        """所有符号组合"""
        results = []
        for sa in [1,-1] if a != 0 else [1]:
            for sb in [1,-1] if b != 0 else [1]:
                for sc in [1,-1] if c != 0 else [1]:
                    results.append((sa*a, sb*b, sc*c))
        return results

    # 三组基础坐标
    groups = [
        (0, 1, 3*p),
        (1, 2+p, 2*p),
        (2, 1+2*p, p),
    ]

    for (a, b, c) in groups:
        for perm in even_perms(a, b, c):
            for signs in sign_combos(*perm):
                v = np.array(signs, dtype=float)
                raw.append(v)

    # 去重（浮点精度）
    unique = []
    for v in raw:
        is_dup = False
        for u in unique:
            if np.linalg.norm(v - u) < 1e-9:
                is_dup = True
                break
        if not is_dup:
            unique.append(v)

    return unique

def _build_adjacency(verts, edge_len_tol=0.15):
    """
    建立顶点邻接表。
    截角二十面体边长约为 1（在我们的坐标系中），
    容差 ±0.15 找到所有边。
    """
    n = len(verts)
    # 先计算理论边长：任意两顶点最短距离
    min_dist = float('inf')
    for i in range(n):
        for j in range(i+1, n):
            d = np.linalg.norm(verts[i] - verts[j])
            if d > 1e-6:
                min_dist = min(min_dist, d)

    edge_len = min_dist
    tol = edge_len * edge_len_tol

    adj = {i: [] for i in range(n)}
    for i in range(n):
        for j in range(i+1, n):
            d = np.linalg.norm(verts[i] - verts[j])
            if abs(d - edge_len) < tol:
                adj[i].append(j)
                adj[j].append(i)

    return adj, edge_len

def _find_faces(verts, adj):
    """
    从邻接表重建所有面（五边形 + 六边形）。
    策略：对每条边 (i,j)，找公共邻居，然后扩展成最小环。
    使用"面中心角排序"法提取多边形面。
    """
    n = len(verts)
    faces = []
    visited_face_centers = []

    def vec_angle_on_plane(center, normal, ref, v):
        """计算 v 在以 center 为原点、normal 为法线的平面上相对于 ref 的角度"""
        proj = lambda x: x - np.dot(x, normal) * normal
        r = proj(ref - center)
        p = proj(v - center)
        cos_a = np.dot(r, p) / (np.linalg.norm(r) * np.linalg.norm(p) + 1e-12)
        sin_a = np.dot(np.cross(r, p), normal) / (np.linalg.norm(r) * np.linalg.norm(p) + 1e-12)
        return math.atan2(sin_a, cos_a)

    # 对每个顶点，找其所在的所有多边形
    # 方法：对 i 的每对相邻顶点 j,k，看能否在它们之间找一条共面的短路径
    # 更可靠的方法：直接用平面拟合

    # 更简单且正确的方法：
    # 截角二十面体每个顶点度数=3，每个顶点属于：1个五边形+2个六边形
    # 对每个顶点 i，其3个邻居 a,b,c 两两之间各有一个公共邻居（除了 i）
    # 那个公共邻居和 i,a,b 一起构成面的一部分

    # 使用"行走"算法：从边 i->j 出发，每次转向最小左转角
    def walk_face(start, second):
        """从 start->second 出发，沿左侧行走找到完整面"""
        face = [start, second]
        prev, curr = start, second
        for _ in range(10):  # 最多10步（六边形=6）
            # 找 curr 的邻居中，相对 prev->curr 方向最"右转"（即最小左转）的
            direction = verts[curr] - verts[prev]
            normal_approx = _norm(np.cross(direction, verts[curr]))  # 粗略法线

            best_angle = float('inf')
            best_next = None
            for nb in adj[curr]:
                if nb == prev:
                    continue
                next_dir = verts[nb] - verts[curr]
                # 计算转角（在 curr 处）
                # 使用叉积判断左右
                angle = math.atan2(
                    np.linalg.norm(np.cross(direction, next_dir)),
                    np.dot(direction, next_dir)
                )
                # 我们要找最小正转角（最右转，即紧贴边界）
                cross = np.cross(direction, next_dir)
                # 使用面法线方向判断符号
                center_approx = np.mean([verts[v] for v in face], axis=0)
                outward = _norm(center_approx)
                signed_angle = math.atan2(np.dot(cross, outward), np.dot(direction, next_dir))
                # 转换到 [0, 2π]
                signed_angle = signed_angle % (2 * math.pi)
                if signed_angle < best_angle:
                    best_angle = signed_angle
                    best_next = nb

            if best_next is None or best_next == face[0]:
                break
            if best_next in face:
                break
            face.append(best_next)
            prev, curr = curr, best_next

        # 验证面：回到起点
        if best_next == face[0] or (len(face) >= 5 and face[-1] in adj[face[0]]):
            return face
        return None

    found_faces = set()

    for i in range(n):
        for j in adj[i]:
            face = walk_face(i, j)
            if face is None:
                continue
            if len(face) not in (5, 6):
                continue
            # 规范化面（最小旋转表示）
            key = tuple(sorted(face))
            if key in found_faces:
                continue
            found_faces.add(key)
            faces.append(face)

    return faces

def _get_face_normal_and_tangent(face_verts):
    """
    计算面法线（朝外）和切向量（指向第一条边中点）。
    face_verts: 有序顶点列表 (np.array)
    """
    center = np.mean(face_verts, axis=0)
    # 法线：Newell 方法
    n = len(face_verts)
    normal = np.zeros(3)
    for k in range(n):
        v0 = face_verts[k]
        v1 = face_verts[(k+1) % n]
        normal[0] += (v0[1] - v1[1]) * (v0[2] + v1[2])
        normal[1] += (v0[2] - v1[2]) * (v0[0] + v1[0])
        normal[2] += (v0[0] - v1[0]) * (v0[1] + v1[1])

    # 确保法线朝外（指向远离原点方向）
    if np.dot(normal, center) < 0:
        normal = -normal
    normal = _norm(normal)

    # 切向：
    # polygon_model 期望 tangent 是“沿边方向”的向量，而不是 center->edge-mid 的径向。
    # 因此这里先生成边中点径向，再通过 normal×radial 旋转 90° 得到沿边方向，
    # 最后吸附到最接近稳定参考方向的一项。
    axes = [
        np.array([1.0, 0.0, 0.0]),
        np.array([0.0, 1.0, 0.0]),
        np.array([0.0, 0.0, 1.0]),
    ]
    ref_axis = min(axes, key=lambda a: abs(np.dot(a, normal)))
    ref = ref_axis - np.dot(ref_axis, normal) * normal
    ref = _norm(ref)

    edge_tangents = []
    n = len(face_verts)
    for i in range(n):
        edge_mid = (face_verts[i] + face_verts[(i + 1) % n]) / 2
        radial = edge_mid - center
        radial = radial - np.dot(radial, normal) * normal
        if np.linalg.norm(radial) <= 1e-12:
            continue
        radial = _norm(radial)
        edge_dir = np.cross(normal, radial)
        if np.linalg.norm(edge_dir) > 1e-12:
            edge_tangents.append(_norm(edge_dir))

    if not edge_tangents:
        tangent = ref
    else:
        tangent = max(edge_tangents, key=lambda d: np.dot(d, ref))

    return normal, tangent, center

def _matrix_from_normal_tangent(normal, tangent):
    """
    构造旋转矩阵：
      col0 = tangent  (X 轴 → 面内"边方向")
      col1 = bitangent = normal × tangent  (Y 轴)
      col2 = normal   (Z 轴 → 朝外法线)
    """
    bitangent = np.cross(normal, tangent)
    bitangent = _norm(bitangent)
    tangent = _norm(np.cross(bitangent, normal))  # 重新正交化
    R = np.column_stack([tangent, bitangent, normal])
    return R

def rotation_matrix_to_euler_xyz(R):
    """
    将旋转矩阵转换为 Blockbench/Bedrock 使用的 XYZ 欧拉角（度）。
    约定：先绕 X，再绕 Y，再绕 Z（内旋）。
    等价于 R = Rz * Ry * Rx（外旋）。
    """
    # R = Rz(z) * Ry(y) * Rx(x)
    # R[2,0] = -sin(y)
    sy = -R[2, 0]
    sy = max(-1.0, min(1.0, sy))
    y = math.asin(sy)

    if abs(math.cos(y)) > 1e-6:
        x = math.atan2(R[2, 1], R[2, 2])
        z = math.atan2(R[1, 0], R[0, 0])
    else:
        # Gimbal lock
        x = math.atan2(-R[1, 2], R[1, 1])
        z = 0.0

    return (math.degrees(x), math.degrees(y), math.degrees(z))

def matrix_multiply(R1, R2):
    """R_final = R1 @ R2"""
    return R1 @ R2

def combine_rotations(R_face, R_local):
    """
    组合面旋转和局部 cuboid 旋转。
    R_final = R_face @ R_local
    """
    return R_face @ R_local

# ─────────────────────────────────────────────
# 2. 主接口
# ─────────────────────────────────────────────

def get_face_transforms(inradius=8.0, origin=(8.0, 8.0, 8.0)):
    """
    返回截角二十面体 32 个面的完整变换数据，缩放到给定 inradius。

    返回列表，每个元素：
    {
        'type'      : 'penta' | 'hexa',
        'center'    : np.array([x,y,z]),  # 面中心（世界坐标，以 origin 为球心）
        'normal'    : np.array([x,y,z]),  # 单位法线（朝外）
        'tangent'   : np.array([x,y,z]),  # 单位切向（面内 X）
        'bitangent' : np.array([x,y,z]),  # 单位切向（面内 Y）
        'R'         : np.ndarray(3,3),    # 旋转矩阵
        'euler_xyz' : (rx, ry, rz),       # Blockbench XYZ 欧拉角（度）
    }
    """
    verts_raw = _truncated_icosahedron_vertices()
    adj, edge_len = _build_adjacency(verts_raw)
    verts = [np.array(v, dtype=float) for v in verts_raw]

    # 找到所有面
    faces_idx = _find_faces(verts, adj)

    # 验证：应该有 12 个五边形 + 20 个六边形 = 32 个面
    pentas = [f for f in faces_idx if len(f) == 5]
    hexas  = [f for f in faces_idx if len(f) == 6]

    if len(pentas) != 12 or len(hexas) != 20:
        raise ValueError(
            f"面数量错误：找到 {len(pentas)} 个五边形，{len(hexas)} 个六边形。"
            f"期望 12 个五边形 + 20 个六边形。\n"
            f"总顶点数：{len(verts)}，总面数：{len(faces_idx)}"
        )

    # 计算缩放比：让面中心距离 = inradius
    # 先计算单位几何的面中心距离
    all_face_center_dists = []
    for f in faces_idx:
        fv = [verts[i] for i in f]
        center = np.mean(fv, axis=0)
        all_face_center_dists.append(np.linalg.norm(center))

    # 截角二十面体有两种 inradius：五边形面和六边形面
    penta_centers = [np.mean([verts[i] for i in f], axis=0) for f in pentas]
    hexa_centers  = [np.mean([verts[i] for i in f], axis=0) for f in hexas]

    avg_penta_r = np.mean([np.linalg.norm(c) for c in penta_centers])
    avg_hexa_r  = np.mean([np.linalg.norm(c) for c in hexa_centers])

    # 用户给定 inradius 约定为“最近面到球心”的距离；
    # 对截角二十面体而言六边形面中心更靠近球心，因此应以六边形面中心距为缩放基准。
    # 这也与 get_geometry_params 的 edge_length 计算保持一致。
    scale = inradius / avg_hexa_r

    origin = np.array(origin, dtype=float)

    result = []
    for face_type, face_list in [('penta', pentas), ('hexa', hexas)]:
        for f in face_list:
            fv = [verts[i] * scale for i in f]
            normal, tangent, center = _get_face_normal_and_tangent(fv)
            bitangent = np.cross(normal, tangent)
            bitangent = _norm(bitangent)
            R = _matrix_from_normal_tangent(normal, tangent)
            euler = rotation_matrix_to_euler_xyz(R)

            result.append({
                'type'      : face_type,
                'center'    : center + origin,
                'normal'    : normal,
                'tangent'   : tangent,
                'bitangent' : bitangent,
                'R'         : R,
                'euler_xyz' : euler,
                'face_verts': [v + origin for v in fv],
            })

    return result


def get_geometry_params(inradius=8.0):
    """
    根据给定 inradius 计算截角二十面体几何参数。

    截角二十面体（标准边长=1）各半径：
      外接球半径  r_out  = sqrt(58+18*sqrt(5)) / 4 * a
      中球半径    r_mid  = (3+4*sqrt(5)) / (2*sqrt(58+18*sqrt(5))) * ... (见 Wikipedia)
      五边形面 inradius r_in5 = a/2 * sqrt(25+10*sqrt(5)) / sqrt(5) * ...

    这里用数值比例（基于单位边长）：
    """
    # 单位边长的截角二十面体各半径（数值）
    PHI = (1 + math.sqrt(5)) / 2
    a = 1.0  # 边长

    # 外接球（顶点到球心）
    r_out_unit = math.sqrt(58 + 18 * math.sqrt(5)) / 4 * a  # ≈ 2.4785

    # 五边形面中心到球心（五边形 apothem of sphere）
    # = a/2 * (3 + 4*phi) / sqrt(3 + 4*phi^2 - ... )
    # 直接用数值：单位边长时，五边形面中心距 ≈ 2.3276
    # 六边形面中心距 ≈ 2.2272
    # 参考：https://mathworld.wolfram.com/TruncatedIcosahedron.html

    # 精确公式（来自顶点计算）：
    # 五边形面中心 = 平均顶点，归一化后乘以距离
    # 这里直接计算
    verts_raw = _truncated_icosahedron_vertices()

    # 找到边长
    min_dist = float('inf')
    n = len(verts_raw)
    for i in range(min(n, 20)):  # 采样加速
        for j in range(n):
            d = np.linalg.norm(verts_raw[i] - verts_raw[j])
            if d > 1e-6:
                min_dist = min(min_dist, d)
    edge_len_unit = min_dist

    adj, _ = _build_adjacency(verts_raw)
    faces_idx = _find_faces(verts_raw, adj)
    pentas = [f for f in faces_idx if len(f) == 5]
    hexas  = [f for f in faces_idx if len(f) == 6]

    penta_r = np.mean([np.linalg.norm(np.mean([verts_raw[i] for i in f], axis=0))
                       for f in pentas])
    hexa_r  = np.mean([np.linalg.norm(np.mean([verts_raw[i] for i in f], axis=0))
                       for f in hexas])
    out_r   = np.mean([np.linalg.norm(v) for v in verts_raw])

    # 真正的 inradius = 最近面（六边形面）到球心距离
    # 六边形面比五边形面更靠近球心
    scale = inradius / hexa_r

    return {
        'inradius'           : inradius,
        'edge_length'        : edge_len_unit * scale,
        'outer_radius'       : out_r * scale,
        'penta_face_radius'  : penta_r * scale,
        'hexa_face_radius'   : hexa_r * scale,
        'scale_factor'       : scale,
    }


# ─────────────────────────────────────────────
# 3. 调试 / 验证
# ─────────────────────────────────────────────
if __name__ == '__main__':
    params = get_geometry_params(inradius=8.0)
    print("=== 几何参数 ===")
    for k, v in params.items():
        print(f"  {k:25s} = {v:.6f}" if isinstance(v, float) else f"  {k:25s} = {v}")

    print("\n=== 面变换 ===")
    faces = get_face_transforms(inradius=8.0, origin=(8,8,8))
    pentas = [f for f in faces if f['type']=='penta']
    hexas  = [f for f in faces if f['type']=='hexa']
    print(f"  五边形面: {len(pentas)} 个")
    print(f"  六边形面: {len(hexas)} 个")

    print("\n前3个五边形面:")
    for i, f in enumerate(pentas[:3]):
        c = f['center']
        e = f['euler_xyz']
        print(f"  penta[{i}] center=({c[0]:.3f},{c[1]:.3f},{c[2]:.3f})"
              f"  euler=({e[0]:.2f},{e[1]:.2f},{e[2]:.2f})")

    print("\n前3个六边形面:")
    for i, f in enumerate(hexas[:3]):
        c = f['center']
        e = f['euler_xyz']
        print(f"  hexa[{i}] center=({c[0]:.3f},{c[1]:.3f},{c[2]:.3f})"
              f"  euler=({e[0]:.2f},{e[1]:.2f},{e[2]:.2f})")
