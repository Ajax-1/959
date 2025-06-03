import bpy
import os
import sys
import math
import bmesh
from mathutils import Vector, Matrix
import logging
import urllib.request
import tempfile
import shutil
import urllib.parse

'''
基于UI_V2-4.28_end版本开发的无界面版本
'''

# 设置日志记录
logging.basicConfig(level=logging.INFO, format='[执行日志] %(message)s')
logger = logging.getLogger(__name__)


# === 初始化函数定义 ===
def project_from_view_manual(obj, camera):
    """基于透视相机进行精确UV投影"""
    logger.info(f"基于透视相机 {camera.name} 参数计算UV投影")

    # 确保在编辑模式
    if obj.mode != 'EDIT':
        bpy.ops.object.mode_set(mode='EDIT')

    mesh = obj.data
    bm = bmesh.from_edit_mesh(mesh)

    # 获取UV层
    uv_layer = bm.loops.layers.uv.verify()

    # 获取相机参数
    cam_data = camera.data
    is_persp = cam_data.type == 'PERSP'  # 确认是透视相机

    # 获取相机到世界的转换矩阵及其逆矩阵
    cam_matrix_world = camera.matrix_world
    cam_matrix_world_inv = cam_matrix_world.inverted()

    # 获取选中的面
    selected_faces = [f for f in bm.faces if f.select]
    if not selected_faces:
        logger.warning("没有选中的面，无法进行UV投影")
        return

    # 输出调试信息
    logger.info(f"相机类型: {'透视' if is_persp else '正交'}")
    logger.info(f"相机位置: {cam_matrix_world.translation}")

    # 为透视相机获取视野和宽高比
    if is_persp:
        sensor_width = cam_data.sensor_width
        sensor_height = cam_data.sensor_height
        focal_length = cam_data.lens

        # 计算视野 (FOV)
        fov = 2 * math.atan(sensor_width / (2 * focal_length))
        aspect_ratio = sensor_width / sensor_height

        logger.info(f"焦距: {focal_length}mm, 视野: {math.degrees(fov):.1f}°, 宽高比: {aspect_ratio:.2f}")

    # 应用UV坐标
    for face in selected_faces:
        for loop in face.loops:
            # 获取顶点的全局坐标
            vert_global = obj.matrix_world @ loop.vert.co

            # 将顶点转换到相机空间
            vert_view = cam_matrix_world_inv @ vert_global

            # 针对透视相机的UV计算
            if is_persp:
                # 透视投影 - 与Blender内部算法更接近
                # z轴指向相机后方，所以需要用-z
                if vert_view.z < 0:  # 确保在相机前方（z为负值表示在相机前方）
                    # 透视除法
                    screen_x = vert_view.x / -vert_view.z
                    screen_y = vert_view.y / -vert_view.z

                    # 根据视野和宽高比调整
                    fov_factor = math.tan(fov / 2)
                    u = 0.5 + screen_x / (2 * fov_factor * aspect_ratio)
                    v = 0.5 + screen_y / (2 * fov_factor)
                else:
                    # 如果顶点在相机后面，设置默认值
                    u, v = 0.5, 0.5
            else:
                # 正交投影（以防相机类型不是透视）
                u = 0.5 + vert_view.x / 10.0
                v = 0.5 + vert_view.y / 10.0

            # 设置UV坐标
            loop[uv_layer].uv = (u, v)

    # 更新网格
    bmesh.update_edit_mesh(mesh)
    logger.info("UV投影计算完成")


def setup_material_with_texture(obj, material_index, texture_path):
    """设置材质并应用纹理"""
    # 获取材质
    mat = obj.material_slots[material_index].material

    # 设置材质节点
    mat.use_nodes = True
    nodes = mat.node_tree.nodes
    nodes.clear()  # 清除默认节点

    # 创建着色器节点
    bsdf = nodes.new(type='ShaderNodeBsdfPrincipled')
    bsdf.location = (300, 0)

    output = nodes.new(type='ShaderNodeOutputMaterial')
    output.location = (500, 0)

    # 连接主着色器到输出
    mat.node_tree.links.new(bsdf.outputs['BSDF'], output.inputs['Surface'])

    # 加载纹理 - 处理本地文件和URL路径
    local_texture_path = download_if_url(texture_path)
    img = bpy.data.images.load(filepath=local_texture_path)

    # 创建图像纹理节点
    tex_img = nodes.new(type='ShaderNodeTexImage')
    tex_img.location = (0, 0)
    tex_img.image = img

    # 连接纹理到着色器
    mat.node_tree.links.new(tex_img.outputs['Color'], bsdf.inputs['Base Color'])

    logger.info(f"已应用纹理 {texture_path} 到材质 {mat.name}")
    return mat

def download_if_url(file_path):
    """如果路径是URL则下载文件，返回本地路径"""
    if file_path.startswith(('http://', 'https://')):
        try:
            # 创建临时文件名
            file_name = os.path.basename(urllib.parse.urlparse(file_path).path)
            # 确保文件名不为空
            if not file_name:
                file_name = "temp_" + str(hash(file_path))
            
            temp_dir = tempfile.gettempdir()
            local_path = os.path.join(temp_dir, file_name)
            
            # 下载文件
            logger.info(f"从URL下载文件: {file_path} 到 {local_path}")
            urllib.request.urlretrieve(file_path, local_path)
            
            logger.info(f"文件下载成功: {local_path}")
            return local_path
        except Exception as e:
            logger.error(f"下载文件失败: {str(e)}")
            raise RuntimeError(f"下载文件失败: {str(e)}")
    else:
        # 已经是本地路径
        return file_path

# === 主脚本开始 ===
logger.info("开始执行无界面Blender脚本...")

# 在脚本开头添加命令行参数处理
def parse_command_line_args():
    """处理命令行参数"""
    # 默认值
    model_path = "/Users/ajax/Desktop/YJS/5_22_test_blender2.1/data/model/02_chuizhi.ply"
    top_texture_path = "/Users/ajax/Desktop/YJS/5_22_test_blender2.1/data/texture/20250522/top.jpg"
    side_texture_path = "/Users/ajax/Desktop/YJS/5_22_test_blender2.1/data/texture/20250522/side.jpg"
    output_path = "/Users/ajax/Desktop/YJS/5_22_test_blender2.1/data/output/02_chuizhi_20250522.glb"
    
    # 检查是否有命令行参数
    argv = sys.argv
    
    # 在Blender中，Python参数位于"--"之后
    if "--" in argv:
        argv = argv[argv.index("--") + 1:]
        
        # 检查参数数量
        if len(argv) >= 1:
            model_path = argv[0]
        if len(argv) >= 2:
            top_texture_path = argv[1]
        if len(argv) >= 3:
            side_texture_path = argv[2]
        if len(argv) >= 4:
            output_path = argv[3]
    
    logger.info(f"使用模型路径: {model_path}")
    logger.info(f"使用顶视图纹理: {top_texture_path}")
    logger.info(f"使用侧视图纹理: {side_texture_path}")
    logger.info(f"输出路径: {output_path}")
    
    return model_path, top_texture_path, side_texture_path, output_path

# 获取命令行参数
model_path, top_texture_path, side_texture_path, output_path = parse_command_line_args()

# 如果参数是URL，下载到临时目录
local_model_path = download_if_url(model_path)

# 清除Blender中的默认对象
logger.info("正在清除默认对象...")
bpy.ops.object.select_all(action='SELECT')
bpy.ops.object.delete()

# 导入PLY文件
logger.info(f"正在导入PLY文件: {local_model_path}")
bpy.ops.wm.ply_import(filepath=local_model_path, files=[{"name": os.path.basename(local_model_path)}])

# 定义相机配置列表（包含模型旋转参数）
camera_configs = [
    {
        "name": "Camera_Top",
        "location": (0, 0, 16),
        "rotation": (0, 0, math.pi / 2),
        "material_name": "Material_Top",
        "texture_path": top_texture_path,
        "model_rotation": (0, 0, 0),  # 模型处理时的旋转角度
        "selection_params": {
            "axis": 2,  # Z轴
            "find_max": True,  # 查找最大值
            "epsilon": 1.5,
            "normal_direction": (0, 0, 1)  # 法线指向Z轴正方向
        }
    },
    {
        "name": "Camera_Side",
        "location": (14, 0, 1.3),
        "rotation": (math.pi / 2, 0, math.pi / 2),
        "material_name": "Material_Side",
        "texture_path": side_texture_path,
        "model_rotation": (0, math.pi / 2, 0),  # 示例：绕Y轴旋转90度
        "selection_params": {
            "axis": 0,  # X轴
            "find_max": True,  # 查找最大值
            "epsilon": 1.5,
            "normal_direction": (1, 0, 0)  # 法线指向X轴正方向
        }
    }
]

# 寻找网格对象
mesh_obj = None
for obj in bpy.context.scene.objects:
    if obj.type == 'MESH':
        mesh_obj = obj
        # 初始化时先保存原始变换
        original_location = mesh_obj.location.copy()
        original_rotation = mesh_obj.rotation_euler.copy()
        original_scale = mesh_obj.scale.copy()
        logger.info(f"已保存模型 {mesh_obj.name} 的原始变换属性")
        break

if not mesh_obj:
    logger.error("场景中没有找到网格对象")
    sys.exit(1)

# 清理临时模型文件
if local_model_path != model_path:
    try:
        logger.info(f"清理临时模型文件: {local_model_path}")
        os.remove(local_model_path)
    except:
        logger.warning(f"无法清理临时文件: {local_model_path}")

# 确保有足够的材质槽
while len(mesh_obj.material_slots) < len(camera_configs):
    bpy.context.view_layer.objects.active = mesh_obj
    bpy.ops.object.material_slot_add()

# 创建材质
for i, config in enumerate(camera_configs):
    mat = bpy.data.materials.new(name=config["material_name"])
    mesh_obj.material_slots[i].material = mat
    mat.use_nodes = True
    logger.info(f"已创建材质: {config['material_name']}")

# === 预先创建所有相机 ===
cameras = []
logger.info("开始预先创建所有相机...")

# 确保处于对象模式
bpy.ops.object.mode_set(mode='OBJECT')
bpy.ops.object.select_all(action='DESELECT')

# 创建所有相机
for i, config in enumerate(camera_configs):
    bpy.ops.object.camera_add(
        enter_editmode=False,
        align='WORLD',
        location=config["location"],
        rotation=config["rotation"]
    )
    camera = bpy.context.active_object
    camera.name = config["name"]
    cameras.append(camera)
    logger.info(f"已创建相机: {config['name']}")

    # 取消选择，避免上下文污染
    bpy.ops.object.select_all(action='DESELECT')

# === 处理每个相机视角和对应的面 ===
for i, config in enumerate(camera_configs):
    camera = cameras[i]
    logger.info(f"开始处理相机配置 {i + 1}/{len(camera_configs)}: {config['name']}")

    # 设置活动相机
    bpy.context.scene.camera = camera

    # 旋转模型到指定角度
    bpy.ops.object.select_all(action='DESELECT')
    mesh_obj.select_set(True)
    bpy.context.view_layer.objects.active = mesh_obj
    bpy.ops.object.mode_set(mode='OBJECT')

    # 调整模型旋转以匹配相机视角
    mesh_obj.rotation_euler = config["model_rotation"]
    logger.info(f"已将模型旋转到相机 {config['name']} 对应的角度")

    # 进入编辑模式选择面
    bpy.ops.object.mode_set(mode='EDIT')
    bm = bmesh.from_edit_mesh(mesh_obj.data)
    matrix_world = mesh_obj.matrix_world

    # 取消所有面的选择
    for face in bm.faces:
        face.select = False

    # 提取选择参数
    sp = config["selection_params"]
    axis = sp["axis"]
    find_max = sp["find_max"]
    epsilon = sp["epsilon"]
    normal_dir = sp["normal_direction"]

    # 计算极值坐标（找出最高点或最远点）
    if find_max:
        extreme_coord = max((matrix_world @ v.co)[axis] for v in bm.verts)
    else:
        extreme_coord = min((matrix_world @ v.co)[axis] for v in bm.verts)

    # 选择满足条件的面（位置和法线方向）
    selected_faces_count = 0
    for face in bm.faces:
        # 获取面的世界坐标和法线
        verts_world_coords = [(matrix_world @ v.co)[axis] for v in face.verts]
        normal_world = matrix_world.to_3x3() @ face.normal

        # 检查面的位置条件
        coord_condition = False
        if find_max:
            coord_condition = all(c >= extreme_coord - epsilon for c in verts_world_coords)
        else:
            coord_condition = all(c <= extreme_coord + epsilon for c in verts_world_coords)

        # 检查面的法线方向条件
        normal_condition = normal_world[axis] * normal_dir[axis] > 0

        # 如果同时满足位置和法线条件，选中此面
        if coord_condition and normal_condition:
            face.select = True
            selected_faces_count += 1

    logger.info(f"已为相机 {config['name']} 选择了 {selected_faces_count} 个面")
    bmesh.update_edit_mesh(mesh_obj.data)

    # 设置活动材质并分配给选中面
    mesh_obj.active_material_index = i
    bpy.ops.object.material_slot_assign()

    # 执行基于相机的UV投影
    project_from_view_manual(mesh_obj, camera)

    # 设置材质和纹理
    setup_material_with_texture(mesh_obj, i, config["texture_path"])

    # 如果不是最后一个相机，恢复模型原始旋转角度以处理下一个视角
    if i < len(camera_configs) - 1:
        bpy.ops.object.mode_set(mode='OBJECT')
        mesh_obj.rotation_euler = original_rotation
        logger.info(f"已恢复模型到原始角度，准备处理下一个相机")

# 确保最后退出编辑模式
bpy.ops.object.mode_set(mode='OBJECT')

# 处理完所有相机后，恢复模型原始状态
mesh_obj.location = original_location
mesh_obj.rotation_euler = original_rotation
mesh_obj.scale = original_scale
logger.info("所有相机处理完成，已将模型恢复到原始状态")

# 在脚本末尾添加保存GLB文件的代码
def save_model_to_glb(output_path):
    """保存模型为GLB格式"""
    logger.info(f"正在保存模型到: {output_path}")
    
    try:
# 确保输出目录存在
        output_dir = os.path.dirname(output_path)
        os.makedirs(output_dir, exist_ok=True)
        
        # 检查目录是否可写
        if not os.access(output_dir, os.W_OK):
            logger.error(f"无法写入输出目录: {output_dir}, 权限不足")
            raise RuntimeError(f"无法写入输出目录: {output_dir}, 权限不足")
        
        # 记录模型信息
        if mesh_obj:
            logger.info(f"模型信息: 名称={mesh_obj.name}, 顶点数={len(mesh_obj.data.vertices)}, 多边形数={len(mesh_obj.data.polygons)}")
            logger.info(f"材质数量: {len(mesh_obj.material_slots)}")
            
        # 设置GLB导出选项
        logger.info("开始GLB导出...")
        bpy.ops.export_scene.gltf(
            filepath=output_path,
            export_format='GLB',
            export_texcoords=True,
            export_normals=True,
            export_materials='EXPORT',
            use_selection=False,
            export_apply=False  # 使用默认值
        )
        
        # 验证文件是否成功保存
        if os.path.exists(output_path):
            file_size = os.path.getsize(output_path)
            logger.info(f"模型已成功保存: {output_path}, 文件大小: {file_size}字节")
        else:
            logger.error(f"导出命令执行完毕，但文件未生成: {output_path}")
            raise RuntimeError(f"文件未生成: {output_path}")
            
    except Exception as e:
        logger.error(f"保存GLB时出错: {str(e)}")
        logger.error(f"错误类型: {type(e)}")
        import traceback
        logger.error(f"错误堆栈: {traceback.format_exc()}")
        raise RuntimeError(f"保存GLB失败: {str(e)}")

# 在主要处理完成后调用保存函数
# 保存处理后的模型
save_model_to_glb(output_path)

logger.info("脚本执行完成!")