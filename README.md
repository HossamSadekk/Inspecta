<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Inspecta Cover</title>
    <style>
        * {
            margin: 0;
            padding: 0;
            box-sizing: border-box;
        }

        body {
            font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
            background: #0a0a0a;
            display: flex;
            justify-content: center;
            align-items: center;
            min-height: 100vh;
            padding: 20px;
        }

        .container {
            width: 100%;
            max-width: 1200px;
        }

        .cover {
            position: relative;
            width: 100%;
            height: 400px;
            background: linear-gradient(135deg, #0f172a 0%, #1e1b4b 50%, #312e81 100%);
            border-radius: 16px;
            overflow: hidden;
            box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.5);
        }

        /* Animated grid background */
        .grid {
            position: absolute;
            top: 0;
            left: 0;
            width: 100%;
            height: 100%;
            background-image: 
                linear-gradient(rgba(139, 92, 246, 0.1) 1px, transparent 1px),
                linear-gradient(90deg, rgba(139, 92, 246, 0.1) 1px, transparent 1px);
            background-size: 50px 50px;
            animation: gridMove 20s linear infinite;
        }

        @keyframes gridMove {
            0% { transform: translate(0, 0); }
            100% { transform: translate(50px, 50px); }
        }

        /* Floating particles */
        .particle {
            position: absolute;
            width: 4px;
            height: 4px;
            background: rgba(139, 92, 246, 0.6);
            border-radius: 50%;
            animation: float 8s infinite ease-in-out;
        }

        .particle:nth-child(1) { top: 20%; left: 10%; animation-delay: 0s; }
        .particle:nth-child(2) { top: 60%; left: 20%; animation-delay: 1s; }
        .particle:nth-child(3) { top: 40%; left: 80%; animation-delay: 2s; }
        .particle:nth-child(4) { top: 80%; left: 70%; animation-delay: 3s; }
        .particle:nth-child(5) { top: 30%; left: 50%; animation-delay: 4s; }
        .particle:nth-child(6) { top: 70%; left: 40%; animation-delay: 1.5s; }
        .particle:nth-child(7) { top: 50%; left: 90%; animation-delay: 2.5s; }
        .particle:nth-child(8) { top: 90%; left: 15%; animation-delay: 3.5s; }

        @keyframes float {
            0%, 100% { transform: translateY(0) translateX(0); opacity: 0.3; }
            50% { transform: translateY(-30px) translateX(20px); opacity: 0.8; }
        }

        /* Content container */
        .content {
            position: relative;
            z-index: 10;
            height: 100%;
            display: flex;
            flex-direction: column;
            justify-content: center;
            align-items: center;
            padding: 60px;
            text-align: center;
        }

        /* Icon container */
        .icon-container {
            margin-bottom: 30px;
            animation: pulse 3s ease-in-out infinite;
        }

        @keyframes pulse {
            0%, 100% { transform: scale(1); }
            50% { transform: scale(1.05); }
        }

        .icon {
            width: 100px;
            height: 100px;
            background: linear-gradient(135deg, #8b5cf6 0%, #6366f1 100%);
            border-radius: 20px;
            display: flex;
            align-items: center;
            justify-content: center;
            box-shadow: 0 20px 40px rgba(139, 92, 246, 0.3);
            position: relative;
        }

        .icon::before {
            content: '📦';
            font-size: 50px;
            animation: rotate 4s linear infinite;
        }

        @keyframes rotate {
            0%, 100% { transform: rotate(0deg); }
            25% { transform: rotate(-5deg); }
            75% { transform: rotate(5deg); }
        }

        /* Title */
        .title {
            font-size: 72px;
            font-weight: 900;
            background: linear-gradient(135deg, #ffffff 0%, #a78bfa 100%);
            -webkit-background-clip: text;
            -webkit-text-fill-color: transparent;
            background-clip: text;
            margin-bottom: 15px;
            letter-spacing: -2px;
            text-shadow: 0 0 30px rgba(139, 92, 246, 0.5);
        }

        /* Subtitle */
        .subtitle {
            font-size: 20px;
            color: #cbd5e1;
            font-weight: 400;
            margin-bottom: 25px;
            letter-spacing: 1px;
        }

        /* Tags */
        .tags {
            display: flex;
            gap: 12px;
            flex-wrap: wrap;
            justify-content: center;
        }

        .tag {
            padding: 8px 20px;
            background: rgba(139, 92, 246, 0.2);
            border: 1px solid rgba(139, 92, 246, 0.3);
            border-radius: 20px;
            color: #c4b5fd;
            font-size: 13px;
            font-weight: 600;
            letter-spacing: 0.5px;
            backdrop-filter: blur(10px);
            transition: all 0.3s ease;
        }

        .tag:hover {
            background: rgba(139, 92, 246, 0.3);
            border-color: rgba(139, 92, 246, 0.5);
            transform: translateY(-2px);
        }

        /* Decorative elements */
        .corner-accent {
            position: absolute;
            width: 200px;
            height: 200px;
            border-radius: 50%;
            background: radial-gradient(circle, rgba(139, 92, 246, 0.2) 0%, transparent 70%);
            filter: blur(40px);
        }

        .corner-accent.top-left {
            top: -100px;
            left: -100px;
        }

        .corner-accent.bottom-right {
            bottom: -100px;
            right: -100px;
            background: radial-gradient(circle, rgba(99, 102, 241, 0.2) 0%, transparent 70%);
        }

        /* Download button */
        .download-btn {
            position: absolute;
            top: 20px;
            right: 20px;
            padding: 12px 24px;
            background: rgba(139, 92, 246, 0.2);
            border: 1px solid rgba(139, 92, 246, 0.4);
            border-radius: 8px;
            color: #e0e7ff;
            font-size: 14px;
            font-weight: 600;
            cursor: pointer;
            backdrop-filter: blur(10px);
            transition: all 0.3s ease;
            z-index: 100;
        }

        .download-btn:hover {
            background: rgba(139, 92, 246, 0.3);
            border-color: rgba(139, 92, 246, 0.6);
            transform: translateY(-2px);
            box-shadow: 0 10px 20px rgba(139, 92, 246, 0.3);
        }

        .instructions {
            margin-top: 30px;
            padding: 20px;
            background: rgba(15, 23, 42, 0.8);
            border-radius: 12px;
            border: 1px solid rgba(139, 92, 246, 0.2);
            text-align: left;
        }

        .instructions h3 {
            color: #c4b5fd;
            margin-bottom: 10px;
            font-size: 18px;
        }

        .instructions p {
            color: #cbd5e1;
            line-height: 1.6;
            font-size: 14px;
        }

        .instructions code {
            background: rgba(139, 92, 246, 0.2);
            padding: 2px 8px;
            border-radius: 4px;
            color: #e0e7ff;
            font-family: 'Monaco', monospace;
            font-size: 13px;
        }
    </style>
</head>
<body>
    <div class="container">
        <div class="cover" id="coverImage">
            <button class="download-btn" onclick="downloadImage()">
                📥 Download Cover (1200x400)
            </button>

            <div class="corner-accent top-left"></div>
            <div class="corner-accent bottom-right"></div>
            
            <div class="grid"></div>
            
            <div class="particle"></div>
            <div class="particle"></div>
            <div class="particle"></div>
            <div class="particle"></div>
            <div class="particle"></div>
            <div class="particle"></div>
            <div class="particle"></div>
            <div class="particle"></div>
            
            <div class="content">
                <div class="icon-container">
                    <div class="icon"></div>
                </div>
                
                <h1 class="title">INSPECTA</h1>
                <p class="subtitle">Android App Size Auditor</p>
                
                <div class="tags">
                    <span class="tag">📦 APK Analysis</span>
                    <span class="tag">🖼️ Asset Optimization</span>
                    <span class="tag">📊 Size Breakdown</span>
                    <span class="tag">🔍 Bloat Detection</span>
                </div>
            </div>
        </div>

        <div class="instructions">
            <h3>📝 How to Use This Cover:</h3>
            <p>
                1. Click the <strong>"Download Cover"</strong> button above<br>
                2. Save the image as <code>inspecta-cover.png</code><br>
                3. Add to your README.md: <code>![Inspecta Cover](inspecta-cover.png)</code><br>
                4. The image is 1200x400px - perfect for GitHub repository headers!
            </p>
        </div>
    </div>

    <script>
        function downloadImage() {
            // Create a canvas
            const cover = document.getElementById('coverImage');
            const canvas = document.createElement('canvas');
            const ctx = canvas.getContext('2d');
            
            // Set canvas size (1200x400 is optimal for GitHub)
            canvas.width = 1200;
            canvas.height = 400;
            
            // Use html2canvas alternative - create image from DOM
            html2canvas(cover, {
                canvas: canvas,
                backgroundColor: null,
                scale: 2, // Higher quality
                width: 1200,
                height: 400
            }).then(function(canvas) {
                // Convert to blob and download
                canvas.toBlob(function(blob) {
                    const url = URL.createObjectURL(blob);
                    const a = document.createElement('a');
                    a.href = url;
                    a.download = 'inspecta-cover.png';
                    document.body.appendChild(a);
                    a.click();
                    document.body.removeChild(a);
                    URL.revokeObjectURL(url);
                });
            });
        }

        // Fallback download using screenshot API
        async function downloadImage() {
            const cover = document.getElementById('coverImage');
            
            try {
                // Method 1: Try to use modern screenshot API if available
                const canvas = await html2canvas(cover, {
                    scale: 2,
                    width: 1200,
                    height: 400,
                    backgroundColor: null
                });
                
                canvas.toBlob((blob) => {
                    const url = URL.createObjectURL(blob);
                    const a = document.createElement('a');
                    a.href = url;
                    a.download = 'inspecta-cover.png';
                    a.click();
                    URL.revokeObjectURL(url);
                });
            } catch (e) {
                // Method 2: Simple fallback - take a screenshot manually
                alert('Right-click on the cover image and select "Save Image As..." to download.\n\nOr use a screenshot tool to capture the cover area (1200x400px recommended).');
            }
        }

        // Load html2canvas from CDN
        const script = document.createElement('script');
        script.src = 'https://cdnjs.cloudflare.com/ajax/libs/html2canvas/1.4.1/html2canvas.min.js';
        document.head.appendChild(script);
    </script>
</body>
</html>
