# ===========================================
# 檔案：models/__init__.py (Package 初始化)
# ===========================================

# 從子模組導入類別，讓 package 使用更方便
from .book import Book
from .customer import Customer

# 定義 package 的公開介面
__all__ = ['Book', 'Customer']