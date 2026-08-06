const fetch = require('node-fetch');
module.exports = async (args) => {
  const token = process.env.REPLIT_TOKEN || '<YOUR_REPLIT_API_TOKEN>';
  const resp = await fetch('https://api.replit.com/v0/repls', {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({ language: args.language, title: args.title })
  });
  const data = await resp.json();
  return data;
};
