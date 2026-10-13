import matplotlib.pyplot as plt
from PIL import Image
import numpy as np

def perp_dist(p, p1, p2):
    if np.all(p1 == p2):
        return np.linalg.norm(p - p1)
    v1 = p2 - p1
    v2 = p1 - p
    return np.abs(v1[0]*v2[1] - v1[1]*v2[0]) / np.linalg.norm(v1)

def rdp(points, epsilon):
    dmax = 0.0
    index = 0
    end = len(points) - 1
    for i in range(1, end):
        d = perp_dist(points[i], points[0], points[-1])
        if d > dmax:
            index = i
            dmax = d
    if dmax > epsilon:
        rec1 = rdp(points[:index+1], epsilon)
        rec2 = rdp(points[index:], epsilon)
        return np.vstack((rec1[:-1], rec2))
    else:
        return np.vstack((points[0], points[-1]))

def main():
    im = Image.open('notification_icon.png')
    arr = np.array(im)
    alpha = (arr[:, :, 3] > 128).astype(float)
    cs = plt.contour(alpha, levels=[0.5])
    paths = cs.allsegs[0]

    simplified_paths = []
    all_pts = []
    for seg in paths:
        simp = rdp(seg, epsilon=4.0)
        simplified_paths.append(simp)
        all_pts.append(simp)

    all_pts_arr = np.vstack(all_pts)
    min_x, min_y = all_pts_arr.min(axis=0)
    max_x, max_y = all_pts_arr.max(axis=0)
    w = max_x - min_x
    h = max_y - min_y
    print(f"BBox: x=[{min_x:.1f}, {max_x:.1f}] (w={w:.1f}), y=[{min_y:.1f}, {max_y:.1f}] (h={h:.1f})")

    # Target viewport 24x24, target content size ~20x20 centered
    target_size = 20.0
    scale = target_size / max(w, h)
    offset_x = 2.0 + (target_size - w * scale) / 2.0 - min_x * scale
    offset_y = 2.0 + (target_size - h * scale) / 2.0 - min_y * scale

    path_data_list = []
    for simp in simplified_paths:
        d = []
        for i, p in enumerate(simp):
            x = p[0] * scale + offset_x
            y = p[1] * scale + offset_y
            cmd = 'M' if i == 0 else 'L'
            d.append(f"{cmd}{x:.2f},{y:.2f}")
        d.append('Z')
        path_data_list.append(' '.join(d))

    full_path = ' '.join(path_data_list)
    print("Total SVG path length:", len(full_path))

    xml_content = f'''<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="#FFFFFF">
    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="{full_path}" />
</vector>
'''

    with open('app/src/main/res/drawable-anydpi-v24/ic_stat_seal.xml', 'w', encoding='utf-8') as f:
        f.write(xml_content)
    print("Updated app/src/main/res/drawable-anydpi-v24/ic_stat_seal.xml")

if __name__ == '__main__':
    main()
