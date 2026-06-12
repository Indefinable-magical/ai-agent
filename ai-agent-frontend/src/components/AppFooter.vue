<template>
  <footer class="app-footer">
    <div class="footer-content">
      <div class="footer-section">
        <div class="footer-logo">
          <h3>AI超级智能体应用平台</h3>
        </div>
        <div class="footer-links">
          <a href="#">《用户协议》</a>
          <a href="#">《隐私政策》</a>
        </div>
      </div>
      
      <div class="footer-section">
        <h4>友情链接</h4>
        <div class="footer-links">
          <a href="https://github.com/Indefinable-magical" target="_blank">GitHub</a>
        </div>
      </div>
      
      <div class="footer-section">
        <h4>联系我们</h4>
        <div class="footer-links">
          <a href="#">商务合作</a>
          <a href="#">站长：小雨</a>
        </div>
      </div>
    </div>
    
    <div class="footer-bottom">
      <p>© {{ currentYear }} AI超级智能体应用平台 - 让AI为你服务</p>
    </div>
  </footer>
</template>

<script setup>
import { computed } from 'vue'

// 计算当前年份
const currentYear = computed(() => new Date().getFullYear())
</script>

<style scoped>
.app-footer {
  background:
    radial-gradient(circle at 18% 0%, rgba(0, 178, 255, 0.18), transparent 30%),
    radial-gradient(circle at 82% 12%, rgba(20, 200, 147, 0.16), transparent 26%),
    linear-gradient(180deg, #0b1220 0%, #0f172a 100%);
  padding: 0;
  color: #cbd5e1;
  border-top: 1px solid rgba(148, 163, 184, 0.18);
  width: 100%;
  margin-top: auto;
  position: relative;
  z-index: 2;
  overflow: hidden;
}

.app-footer::before {
  content: '';
  position: absolute;
  inset: 0 0 auto;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(0, 240, 255, 0.85), transparent);
}

.app-footer::after {
  content: '';
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(148, 163, 184, 0.045) 1px, transparent 1px),
    linear-gradient(90deg, rgba(148, 163, 184, 0.045) 1px, transparent 1px);
  background-size: 36px 36px;
  pointer-events: none;
}

.footer-content {
  width: min(100% - 96px, 1680px);
  max-width: none;
  margin: 0 auto;
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 56px;
  padding: 56px 0 48px;
  position: relative;
  z-index: 1;
  text-align: center;
}

.footer-section {
  min-width: 200px;
  padding: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.footer-logo h3 {
  font-size: 1.45rem;
  margin: 0 0 22px;
  color: #f8fafc;
  letter-spacing: 0.5px;
  text-shadow: 0 0 18px rgba(0, 240, 255, 0.24);
}

.footer-section h4 {
  font-size: 1rem;
  margin: 0 0 20px;
  color: #f8fafc;
  letter-spacing: 0.4px;
  position: relative;
  padding-bottom: 12px;
}

.footer-section h4::after {
  content: '';
  position: absolute;
  left: 50%;
  bottom: 0;
  width: 34px;
  height: 2px;
  border-radius: 99px;
  background: linear-gradient(90deg, #00f0ff, #14c893);
  transform: translateX(-50%);
}

.footer-links {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}

.footer-links a {
  width: fit-content;
  margin-bottom: 0;
  color: #94a3b8;
  text-decoration: none;
  transition: color 0.2s, transform 0.2s;
  font-size: 0.98rem;
  line-height: 1.5;
}

.footer-links a:hover {
  color: #5eead4;
  transform: translateX(4px);
}

.qrcode {
  display: flex;
  align-items: center;
}

.qrcode-container {
  text-align: center;
}

.qrcode-placeholder {
  width: 90px;
  height: 90px;
  background-color: #f5f5f5;
  margin: 0 auto 10px;
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 2rem;
}

.qrcode-placeholder:after {
  content: '🤖';
}

.footer-bottom {
  text-align: center;
  padding: 22px 20px;
  margin-top: 0;
  border-top: 1px solid rgba(148, 163, 184, 0.16);
  color: #94a3b8;
  font-size: 0.9rem;
  background: rgba(2, 6, 23, 0.36);
  position: relative;
  z-index: 1;
}

.footer-bottom p {
  margin: 0;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .footer-content {
    grid-template-columns: 1fr;
    gap: 28px;
    width: min(100% - 48px, 720px);
    padding: 42px 0 34px;
  }
  
  .footer-section {
    width: 100%;
    padding: 0;
  }
}

@media (max-width: 480px) {
  .footer-content {
    width: calc(100% - 36px);
    padding: 34px 0 28px;
  }

  .footer-logo h3 {
    font-size: 1.2rem;
  }

  .footer-section h4 {
    font-size: 0.95rem;
  }
  
  .footer-links a {
    font-size: 0.9rem;
  }
  
  .qrcode-placeholder {
    width: 80px;
    height: 80px;
  }
}
</style> 
